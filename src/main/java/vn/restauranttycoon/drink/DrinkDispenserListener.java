package vn.restauranttycoon.drink;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.Plugin;
import vn.restauranttycoon.config.DrinkDispenserSettings;
import vn.restauranttycoon.config.DrinkStationSettings;
import vn.restauranttycoon.config.DrinkType;

public final class DrinkDispenserListener implements Listener {
    private final Map<StationLocation, DrinkStationSettings> stations;
    private final DrinkDispenserController controller;
    private final LongSupplier clock;
    private final Consumer<Runnable> deferredExecutor;
    private final Set<StationLocation> pendingPhysicsValidations = new HashSet<>();

    public DrinkDispenserListener(DrinkDispenserSettings settings, Plugin plugin) {
        this(settings, new DrinkDispenserController(settings.fillSeconds() * 1_000_000_000L),
                System::nanoTime,
                task -> plugin.getServer().getScheduler().runTask(plugin, task));
    }

    DrinkDispenserListener(
            DrinkDispenserSettings settings,
            DrinkDispenserController controller,
            LongSupplier clock,
            Consumer<Runnable> deferredExecutor
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.deferredExecutor = Objects.requireNonNull(deferredExecutor, "deferredExecutor");
        this.stations = new HashMap<>();
        for (DrinkStationSettings station : settings.stations()) {
            stations.put(StationLocation.from(station), station);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Block block = event.getClickedBlock();
        DrinkStationSettings station = stations.get(StationLocation.from(block));
        if (station == null) {
            return;
        }

        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(block.getBlockData() instanceof Powerable lever)
                || block.getType() != Material.LEVER) {
            controller.clear(station.stationId());
            event.getPlayer().sendMessage("Quầy nước này không còn là cần gạt hợp lệ.");
            return;
        }

        Player player = event.getPlayer();
        boolean mainHandEmpty = player.getInventory().getItemInMainHand().getType().isAir();
        DrinkDispenserDecision decision = controller.interact(
                station.stationId(),
                player.getUniqueId(),
                lever.isPowered(),
                mainHandEmpty,
                clock.getAsLong());

        lever.setPowered(decision.keepLeverDown());
        block.setBlockData(lever, false);
        if (decision.grantDrink()) {
            player.getInventory().setItemInMainHand(createDrink(station.drinkType()));
        }
        sendFeedback(player, decision.outcome());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        invalidateStation(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        invalidateStation(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(this::invalidateStation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(this::invalidateStation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        invalidateStation(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        event.getBlocks().forEach(this::invalidateStation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        event.getBlocks().forEach(this::invalidateStation);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        validateStationsAroundPhysicsRoot(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                (worldName, x, y, z) -> block.getWorld().getBlockAt(x, y, z).getType()
                        == Material.LEVER);
    }

    public void clear() {
        pendingPhysicsValidations.clear();
        controller.clearAll();
    }

    void validateStationAfterPhysics(
            String worldName,
            int x,
            int y,
            int z,
            BooleanSupplier leverStillPresent
    ) {
        StationLocation location = new StationLocation(worldName, x, y, z);
        DrinkStationSettings station = stations.get(location);
        if (station == null
                || !controller.hasActiveFill(station.stationId())
                || !pendingPhysicsValidations.add(location)) {
            return;
        }
        try {
            deferredExecutor.accept(() -> {
                pendingPhysicsValidations.remove(location);
                if (!leverStillPresent.getAsBoolean()) {
                    controller.clear(station.stationId());
                }
            });
        } catch (RuntimeException exception) {
            pendingPhysicsValidations.remove(location);
            throw exception;
        }
    }

    void invalidateStation(String worldName, int x, int y, int z) {
        DrinkStationSettings station = stations.get(new StationLocation(worldName, x, y, z));
        if (station != null) {
            controller.clear(station.stationId());
        }
    }

    void validateStationsAroundPhysicsRoot(
            String worldName,
            int x,
            int y,
            int z,
            LeverLookup leverLookup
    ) {
        validateStationAfterPhysics(
                worldName, x, y, z, () -> leverLookup.isLever(worldName, x, y, z));
        validateStationAfterPhysics(
                worldName, x + 1, y, z, () -> leverLookup.isLever(worldName, x + 1, y, z));
        validateStationAfterPhysics(
                worldName, x - 1, y, z, () -> leverLookup.isLever(worldName, x - 1, y, z));
        validateStationAfterPhysics(
                worldName, x, y + 1, z, () -> leverLookup.isLever(worldName, x, y + 1, z));
        validateStationAfterPhysics(
                worldName, x, y - 1, z, () -> leverLookup.isLever(worldName, x, y - 1, z));
        validateStationAfterPhysics(
                worldName, x, y, z + 1, () -> leverLookup.isLever(worldName, x, y, z + 1));
        validateStationAfterPhysics(
                worldName, x, y, z - 1, () -> leverLookup.isLever(worldName, x, y, z - 1));
    }

    private void invalidateStation(Block block) {
        invalidateStation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    private ItemStack createDrink(DrinkType drinkType) {
        if (drinkType != DrinkType.WATER) {
            throw new IllegalStateException("Unsupported configured drink type: " + drinkType);
        }
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        meta.setBasePotionType(PotionType.WATER);
        item.setItemMeta(meta);
        return item;
    }

    private void sendFeedback(Player player, DrinkDispenserOutcome outcome) {
        switch (outcome) {
            case STARTED -> player.sendMessage("Đang rót nước. Hãy chờ cho đầy rồi gạt cần lên.");
            case STILL_FILLING -> player.sendMessage("Nước chưa đầy. Cần gạt đã được giữ ở dưới.");
            case DISPENSED -> player.sendMessage("Bạn đã lấy một chai nước.");
            case HAND_MUST_BE_EMPTY -> player.sendMessage("Hãy để trống tay chính để dùng quầy nước.");
            case OWNED_BY_ANOTHER_PLAYER -> player.sendMessage("Quầy nước này đang được người khác sử dụng.");
            case RESET -> player.sendMessage("Không có nước đang chờ tại quầy này.");
        }
    }

    private record StationLocation(String worldName, int x, int y, int z) {
        private static StationLocation from(DrinkStationSettings station) {
            return new StationLocation(station.worldName(), station.x(), station.y(), station.z());
        }

        private static StationLocation from(Block block) {
            return new StationLocation(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }
    }

    @FunctionalInterface
    interface LeverLookup {
        boolean isLever(String worldName, int x, int y, int z);
    }
}
