package vn.restauranttycoon.onboarding;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.config.PlotSettings;
import vn.restauranttycoon.lore.RestaurantLore;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.plot.PlotAssignment;
import vn.restauranttycoon.plot.PlotAssignmentService;
import vn.restauranttycoon.worldoperation.WorldOperationRepository;

public final class OnboardingListener implements Listener, AutoCloseable {
    private static final long INITIAL_STAGE_REVISION = 1;

    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final PlotAssignmentService assignments;
    private final String serverId;
    private final List<String> configuredPlotIds;
    private final Map<String, PlotSettings> plots;
    private final OnboardingLifecycle lifecycle = new OnboardingLifecycle();
    private final Map<UUID, PlotAssignment> activeAssignments = new HashMap<>();
    private final Set<UUID> pendingStageRequests = new HashSet<>();

    public OnboardingListener(
            JavaPlugin plugin,
            DatabaseManager database,
            PlotAssignmentService assignments,
            String serverId,
            List<PlotSettings> plots
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.configuredPlotIds = plots.stream().map(PlotSettings::plotId).toList();
        Map<String, PlotSettings> indexed = new HashMap<>();
        for (PlotSettings plot : plots) {
            indexed.put(plot.plotId(), plot);
        }
        this.plots = Map.copyOf(indexed);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        begin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || sameBlock(from, to)) {
            return;
        }
        PlotAssignment assignment = activeAssignments.get(event.getPlayer().getUniqueId());
        if (assignment == null) {
            return;
        }
        PlotSettings plot = plots.get(assignment.plotId());
        if (plot != null && contains(plot, to)) {
            requestInitialStage(event.getPlayer(), assignment);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lifecycle.invalidate(playerId);
        activeAssignments.remove(playerId);
        pendingStageRequests.remove(playerId);
    }

    public void begin(Player player) {
        UUID playerId = player.getUniqueId();
        OnboardingLifecycle.Ticket ticket = lifecycle.open(playerId);
        assignments.allocate(playerId, serverId, configuredPlotIds)
                .whenComplete((assignment, error) -> runSync(() -> {
                    if (!lifecycle.isCurrent(ticket) || !player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Không thể cấp mảnh đất cho " + playerId
                                + ": " + rootMessage(error));
                        player.sendMessage("Hiện chưa thể cấp mảnh đất. Vui lòng báo quản trị viên.");
                        return;
                    }
                    activeAssignments.put(playerId, assignment);
                    guide(player, assignment);
                    PlotSettings plot = plots.get(assignment.plotId());
                    if (plot != null && contains(plot, player.getLocation())) {
                        requestInitialStage(player, assignment);
                    }
                }));
    }

    private void guide(Player player, PlotAssignment assignment) {
        PlotSettings plot = plots.get(assignment.plotId());
        if (plot == null) {
            player.sendMessage("Mảnh đất đã cấp không còn trong cấu hình. Vui lòng báo quản trị viên.");
            return;
        }
        Location target = new Location(
                plugin.getServer().getWorld(plot.worldName()),
                (plot.trigger().minX() + plot.trigger().maxX()) / 2.0 + 0.5,
                plot.trigger().minY(),
                (plot.trigger().minZ() + plot.trigger().maxZ()) / 2.0 + 0.5);
        if (target.getWorld() == null) {
            player.sendMessage("Thế giới chứa mảnh đất chưa sẵn sàng. Vui lòng báo quản trị viên.");
            return;
        }
        player.setCompassTarget(target);
        RestaurantLore.plotGuide(
                assignment.plotId(), target.getBlockX(), target.getBlockY(), target.getBlockZ())
                .forEach(player::sendMessage);
    }

    private void requestInitialStage(Player player, PlotAssignment assignment) {
        UUID playerId = player.getUniqueId();
        if (!pendingStageRequests.add(playerId)) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return new WorldOperationRepository(database.requireDataSource()).requestInitialStage(
                        playerId,
                        assignment.plotId(),
                        assignment.fenceToken(),
                        INITIAL_STAGE_REVISION);
            } catch (SQLException exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        }, database.executor()).whenComplete((operationId, error) -> runSync(() -> {
            pendingStageRequests.remove(playerId);
            if (!player.isOnline() || activeAssignments.get(playerId) != assignment) {
                return;
            }
            if (error != null) {
                plugin.getLogger().warning("Không thể yêu cầu dựng nhà hàng cho " + playerId
                        + ": " + rootMessage(error));
                player.sendMessage("Chưa thể dựng nhà hàng. Hãy bước ra rồi vào lại khu đất.");
                return;
            }
            player.sendMessage("Đã xác nhận vị trí. Nhà hàng đang được dựng.");
        }));
    }

    private boolean contains(PlotSettings plot, Location location) {
        return location.getWorld() != null
                && plot.trigger().contains(
                        location.getWorld().getName(), plot,
                        location.getX(), location.getY(), location.getZ());
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private void runSync(Runnable action) {
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, action);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public void close() {
        lifecycle.close();
        activeAssignments.clear();
        pendingStageRequests.clear();
    }
}
