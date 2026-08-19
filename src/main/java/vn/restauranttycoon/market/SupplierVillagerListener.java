package vn.restauranttycoon.market;

import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

public final class SupplierVillagerListener implements Listener {
    private final NamespacedKey supplierKey;
    private final MarketMenuController market;

    public SupplierVillagerListener(NamespacedKey supplierKey, MarketMenuController market) {
        this.supplierKey = Objects.requireNonNull(supplierKey, "supplierKey");
        this.market = Objects.requireNonNull(market, "market");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getType() != EntityType.VILLAGER) return;
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        Byte marker = villager.getPersistentDataContainer().get(supplierKey, PersistentDataType.BYTE);
        if (marker == null || marker != (byte) 1) return;
        event.setCancelled(true);
        market.openFromSupplier(event.getPlayer());
    }
}
