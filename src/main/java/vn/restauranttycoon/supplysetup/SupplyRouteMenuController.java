package vn.restauranttycoon.supplysetup;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;

public final class SupplyRouteMenuController implements Listener {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final Set<String> restaurantIds;
    private final SupplyRouteMenuLayout layout = new SupplyRouteMenuLayout();
    private final SupplySetupMessages messages;
    private final Logger logger;

    public SupplyRouteMenuController(
            JavaPlugin plugin,
            DatabaseManager database,
            Set<String> restaurantIds
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.restaurantIds = Set.copyOf(restaurantIds);
        this.messages = new BukkitSupplySetupMessages(plugin);
        this.logger = plugin.getLogger();
    }

    public boolean openFromCommand(Player player, String plotId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(plotId, "plotId");
        if (!player.hasPermission("restauranttycoon.admin.setup")) {
            player.sendMessage(ChatColor.RED + messages.text("menu.no-permission"));
            return true;
        }
        if (database.state() != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + messages.text("menu.database-unavailable"));
            return true;
        }
        if (!restaurantIds.contains(plotId)) {
            player.sendMessage(ChatColor.RED + messages.text(
                    "menu.unknown-restaurant", Map.of("restaurant", plotId)));
            return true;
        }
        SupplySetupOwner owner;
        try {
            owner = SupplySetupOwner.restaurant(plotId);
        } catch (IllegalArgumentException exception) {
            player.sendMessage(ChatColor.RED + messages.text(
                    "menu.unknown-restaurant", Map.of("restaurant", plotId)));
            return true;
        }
        load(player, owner);
        return true;
    }

    private void load(Player player, SupplySetupOwner owner) {
        CompletableFuture.supplyAsync(() -> find(owner), database.executor())
                .whenComplete((route, error) -> sync(() -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        fail(player, error);
                        return;
                    }
                    show(player, owner, route);
                }));
    }

    private SupplyRoute find(SupplySetupOwner owner) {
        try {
            return new SupplyRouteRepository(database.requireDataSource()).findAll(owner);
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void show(Player player, SupplySetupOwner owner, SupplyRoute route) {
        RouteHolder holder = new RouteHolder(owner, route);
        Inventory inventory = Bukkit.createInventory(holder, SupplyRouteMenuLayout.INVENTORY_SIZE,
                messages.text("route.title", Map.of("restaurant", owner.ownerId())));
        holder.inventory = inventory;
        fill(inventory);
        for (SupplyRouteWaypoint waypoint : route.waypoints()) {
            inventory.setItem(layout.waypointSlots().get(waypoint.sequence() - 1),
                    waypointItem(waypoint));
        }
        if (route.waypoints().size() < layout.waypointSlots().size()) {
            inventory.setItem(layout.addSlot(), item(Material.LIME_CONCRETE,
                    messages.text("route.add-name"), List.of(messages.text("route.add-lore"))));
        }
        inventory.setItem(layout.backSlot(), item(Material.ARROW,
                messages.text("route.back-name"), List.of(messages.text("route.back-lore"))));
        player.openInventory(inventory);
    }

    private void fill(Inventory inventory) {
        ItemStack pane = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < SupplyRouteMenuLayout.INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private ItemStack waypointItem(SupplyRouteWaypoint waypoint) {
        SupplySetupPosition position = waypoint.position();
        return item(Material.LIME_CONCRETE,
                ChatColor.GREEN + messages.text("route.waypoint-name", Map.of(
                        "sequence", Integer.toString(waypoint.sequence()), "name", waypoint.name())),
                List.of(
                        ChatColor.GRAY + messages.text("route.waypoint-location", Map.of(
                                "world", position.worldName(),
                                "x", format(position.x()), "y", format(position.y()), "z", format(position.z()))),
                        "",
                        ChatColor.GRAY + messages.text("route.set-lore"),
                        ChatColor.GRAY + messages.text("route.teleport-lore"),
                        ChatColor.GRAY + messages.text("route.delete-lore")));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof ConfirmHolder confirm) {
            event.setCancelled(true);
            if (event.getClickedInventory() != top) return;
            if (event.getSlot() == 11) delete(player, confirm.owner, confirm.sequence);
            if (event.getSlot() == 15) load(player, confirm.owner);
            return;
        }
        if (!(top.getHolder() instanceof RouteHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != top) return;
        int slot = event.getSlot();
        if (slot == layout.backSlot()) {
            player.closeInventory();
            return;
        }
        if (slot == layout.addSlot()) {
            save(player, holder.owner, holder.route.waypoints().size() + 1);
            return;
        }
        int waypointIndex = layout.waypointSlots().indexOf(slot);
        if (waypointIndex < 0) return;
        int sequence = waypointIndex + 1;
        if (sequence > holder.route.waypoints().size()) return;
        if (event.isShiftClick() && event.isRightClick()) {
            confirmDelete(player, holder.owner, sequence);
        } else if (event.isLeftClick()) {
            save(player, holder.owner, sequence);
        } else if (event.isRightClick()) {
            teleport(player, holder.owner, sequence);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof RouteHolder || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void save(Player player, SupplySetupOwner owner, int sequence) {
        Location location = player.getLocation();
        SupplyRouteWaypoint waypoint = new SupplyRouteWaypoint(owner, sequence,
                messages.text("route.default-name", Map.of("sequence", Integer.toString(sequence))),
                new SupplySetupPosition(location.getWorld().getName(), location.getX(), location.getY(),
                        location.getZ(), location.getYaw(), location.getPitch()));
        loadRouteThen(player, owner, route -> {
            List<SupplyRouteWaypoint> points = new java.util.ArrayList<>(route.waypoints());
            while (points.size() < sequence - 1) {
                player.sendMessage(ChatColor.YELLOW + messages.text("route.must-fill-order"));
                return;
            }
            if (points.size() == sequence - 1) points.add(waypoint);
            else points.set(sequence - 1, waypoint);
            replace(player, owner, new SupplyRoute(owner, points));
        });
    }

    private void confirmDelete(Player player, SupplySetupOwner owner, int sequence) {
        ConfirmHolder holder = new ConfirmHolder(owner, sequence);
        Inventory inventory = Bukkit.createInventory(holder, 27, messages.text("route.confirm-title"));
        holder.inventory = inventory;
        ItemStack pane = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, pane.clone());
        inventory.setItem(11, item(Material.RED_CONCRETE, messages.text("route.confirm-delete"), List.of()));
        inventory.setItem(15, item(Material.LIME_CONCRETE, messages.text("route.confirm-cancel"), List.of()));
        player.openInventory(inventory);
    }

    private void teleport(Player player, SupplySetupOwner owner, int sequence) {
        loadRouteThen(player, owner, route -> {
            Optional<SupplyRouteWaypoint> found = route.waypoints().stream()
                    .filter(point -> point.sequence() == sequence).findFirst();
            if (found.isEmpty()) return;
            SupplySetupPosition p = found.get().position();
            World world = Bukkit.getWorld(p.worldName());
            if (world == null) {
                player.sendMessage(ChatColor.RED + messages.text(
                        "feedback.world-unavailable", Map.of("world", p.worldName())));
                return;
            }
            player.teleport(new Location(world, p.x(), p.y(), p.z(), p.yaw(), p.pitch()));
        });
    }

    private void delete(Player player, SupplySetupOwner owner, int sequence) {
        loadRouteThen(player, owner, route -> {
            List<SupplyRouteWaypoint> points = route.waypoints().stream()
                    .filter(point -> point.sequence() != sequence)
                    .map(point -> new SupplyRouteWaypoint(owner,
                            point.sequence() > sequence ? point.sequence() - 1 : point.sequence(),
                            point.name(), point.position())).toList();
            replace(player, owner, new SupplyRoute(owner, points));
        });
    }

    private void loadRouteThen(Player player, SupplySetupOwner owner,
            java.util.function.Consumer<SupplyRoute> action) {
        CompletableFuture.supplyAsync(() -> find(owner), database.executor())
                .whenComplete((route, error) -> sync(() -> {
                    if (error != null) fail(player, error); else action.accept(route);
                }));
    }

    private void replace(Player player, SupplySetupOwner owner, SupplyRoute route) {
        CompletableFuture.runAsync(() -> {
            try {
                new SupplyRouteRepository(database.requireDataSource()).replaceAll(route);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, database.executor()).whenComplete((ignored, error) -> sync(() -> {
            if (error != null) {
                fail(player, error);
            } else {
                load(player, owner);
            }
        }));
    }

    private void sync(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
    private void fail(Player player, Throwable error) { logger.warning("Supply route failed: " + error.getMessage());
        player.sendMessage(ChatColor.RED + messages.text("feedback.operation-failed")); }
    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }
    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name); meta.setLore(lore); item.setItemMeta(meta); return item;
    }

    private static final class RouteHolder implements InventoryHolder {
        private final SupplySetupOwner owner; private final SupplyRoute route; private Inventory inventory;
        private RouteHolder(SupplySetupOwner owner, SupplyRoute route) { this.owner = owner; this.route = route; }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class ConfirmHolder implements InventoryHolder {
        private final SupplySetupOwner owner; private final int sequence; private Inventory inventory;
        private ConfirmHolder(SupplySetupOwner owner, int sequence) { this.owner = owner; this.sequence = sequence; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
