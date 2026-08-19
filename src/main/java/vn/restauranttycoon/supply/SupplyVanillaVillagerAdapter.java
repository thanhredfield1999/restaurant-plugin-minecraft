package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Tạo supplier Villager vanilla. Chỉ gọi trên server thread sau khi DB đã claim shipment. */
public final class SupplyVanillaVillagerAdapter {
    private static final byte MARKER = 1;

    private final JavaPlugin plugin;
    private final SupplyVillagerRegistry registry;
    private final NamespacedKey roleKey;
    private final NamespacedKey shipmentKey;

    public SupplyVanillaVillagerAdapter(JavaPlugin plugin) {
        this(plugin, null);
    }

    public SupplyVanillaVillagerAdapter(JavaPlugin plugin, SupplyVillagerRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = registry;
        this.roleKey = new NamespacedKey(plugin, "supplier_villager");
        this.shipmentKey = new NamespacedKey(plugin, "supply_shipment_id");
    }

    public Villager spawnSupplierAtCheckpoint(
            SupplyRuntimeProjection projection, World world) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(world, "world");
        SupplySetupPosition position = SupplyRuntimeCheckpointResolver.resolve(
                projection.journey(), projection.checkpointStage(), projection.checkpointIndex());
        if (!world.getName().equals(position.worldName())) {
            throw new IllegalArgumentException("loaded world does not match journey checkpoint");
        }
        return spawnSupplier(new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch()),
                projection.shipmentId());
    }

    public Villager spawnSupplier(Location location, UUID shipmentId) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(shipmentId, "shipmentId");
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Villager spawn must run on the server thread");
        }
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Supplier spawn location has no world");
        }
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!location.getWorld().isChunkLoaded(chunkX, chunkZ)) {
            throw new IllegalStateException("Supplier spawn chunk is not loaded");
        }
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCollidable(false);
        villager.setRemoveWhenFarAway(false);
        villager.getPersistentDataContainer().set(roleKey, PersistentDataType.BYTE, MARKER);
        villager.getPersistentDataContainer().set(shipmentKey, PersistentDataType.STRING, shipmentId.toString());
        villager.setCustomName("Nhà cung cấp");
        villager.setCustomNameVisible(true);
        if (registry != null && !registry.register(shipmentId, villager.getUniqueId())) {
            villager.remove();
            throw new IllegalStateException("Supplier registry candidate limit exceeded");
        }
        return villager;
    }

    public SupplyVillagerMovementDecision moveToward(
            Villager villager, Location target, double arrivalRadius, double maxSpeed) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(target, "target");
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Villager movement must run on the server thread");
        }
        if (!villager.isValid() || !villager.isInWorld() || villager.isDead()) {
            throw new IllegalStateException("Supplier Villager is not a live world entity");
        }
        if (target.getWorld() == null || villager.getWorld() != target.getWorld()) {
            throw new IllegalArgumentException("Villager and target must share loaded world");
        }
        SupplyVillagerMovementDecision decision = SupplyVillagerMovementDecision.toward(
                villager.getX(), villager.getY(), villager.getZ(),
                target.getX(), target.getY(), target.getZ(), arrivalRadius, maxSpeed);
        villager.setVelocity(new Vector(decision.velocityX(), decision.velocityY(), decision.velocityZ()));
        return decision;
    }

    public SupplyVillagerMovementOutcome moveToward(
            Villager villager, Location target, double arrivalRadius, double maxSpeed,
            SupplyVillagerStuckWatchdog watchdog) {
        Objects.requireNonNull(watchdog, "watchdog");
        SupplyVillagerMovementDecision decision = moveToward(villager, target, arrivalRadius, maxSpeed);
        if (decision.arrived()) {
            watchdog.reset();
            return SupplyVillagerMovementOutcome.ARRIVED;
        }
        return watchdog.observe(villager.getLocation().distance(target))
                ? SupplyVillagerMovementOutcome.STUCK
                : SupplyVillagerMovementOutcome.MOVING;
    }

    public boolean removeSupplier(Villager villager, UUID expectedShipmentId) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(expectedShipmentId, "expectedShipmentId");
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Villager removal must run on the server thread");
        }
        if (!villager.isValid() || !villager.isInWorld() || villager.isDead()) {
            return false;
        }
        if (!isSupplier(villager) || !expectedShipmentId.equals(shipmentId(villager))) {
            return false;
        }
        villager.remove();
        return true;
    }

    public boolean isSupplier(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        Byte marker = villager.getPersistentDataContainer().get(roleKey, PersistentDataType.BYTE);
        return marker != null && marker == MARKER;
    }

    public UUID shipmentId(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        String value = villager.getPersistentDataContainer().get(shipmentKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
