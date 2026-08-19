package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

public final class SupplyVillagerRegistryListener implements Listener {
    private final SupplyVanillaVillagerAdapter adapter;
    private final SupplyVillagerRegistry registry;

    public SupplyVillagerRegistryListener(
            SupplyVanillaVillagerAdapter adapter,
            SupplyVillagerRegistry registry) {
        this.adapter = Objects.requireNonNull(adapter, "adapter");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        register(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) register(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) unregister(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        unregister(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        unregister(event.getEntity());
    }

    private void register(Entity entity) {
        if (!(entity instanceof Villager villager) || !adapter.isSupplier(villager)) return;
        UUID shipmentId = adapter.shipmentId(villager);
        if (shipmentId != null) registry.register(shipmentId, entity.getUniqueId());
    }

    private void unregister(Entity entity) {
        if (!(entity instanceof Villager villager) || !adapter.isSupplier(villager)) return;
        UUID shipmentId = adapter.shipmentId(villager);
        if (shipmentId != null) registry.unregister(shipmentId, entity.getUniqueId());
    }
}
