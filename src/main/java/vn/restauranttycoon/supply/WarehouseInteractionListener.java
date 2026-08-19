package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;

public final class WarehouseInteractionListener implements Listener {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final NamespacedKey warehouseKey;
    private final NamespacedKey packageKey;

    public WarehouseInteractionListener(
            JavaPlugin plugin, DatabaseManager database, NamespacedKey warehouseKey, NamespacedKey packageKey) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.warehouseKey = Objects.requireNonNull(warehouseKey, "warehouseKey");
        this.packageKey = Objects.requireNonNull(packageKey, "packageKey");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (!(block != null && block.getState() instanceof org.bukkit.block.TileState tile)) return;
        if (!Objects.equals(tile.getPersistentDataContainer().get(warehouseKey, PersistentDataType.BYTE), (byte) 1)) return;
        event.setCancelled(true);
        ItemStack hand = event.getItem();
        String encoded = hand == null ? null
                : hand.getItemMeta() == null ? null
                : hand.getItemMeta().getPersistentDataContainer().get(packageKey, PersistentDataType.STRING);
        if (encoded == null) {
            event.getPlayer().sendMessage(ChatColor.YELLOW + "Cầm package để nhập kho.");
            return;
        }
        final UUID packageId;
        try {
            packageId = UUID.fromString(encoded);
        } catch (IllegalArgumentException exception) {
            event.getPlayer().sendMessage(ChatColor.RED + "Package ID không hợp lệ.");
            return;
        }
        Player player = event.getPlayer();
        if (database.state() != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + "Cơ sở dữ liệu chưa sẵn sàng.");
            return;
        }
        database.executor().execute(() -> {
            try {
                new SupplyFulfillmentRepository(database.requireDataSource()).stockForOrderPlayer(
                        packageId, player.getUniqueId(), UUID.randomUUID());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    removePackageToken(player, packageId);
                    if (player.isOnline()) player.sendMessage(ChatColor.GREEN + "Đã nhập package vào kho.");
                });
            } catch (Exception exception) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(ChatColor.RED + "Không thể nhập kho: " + rootMessage(exception));
                });
            }
        });
    }

    private void removePackageToken(Player player, UUID packageId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || !item.hasItemMeta()) continue;
            String encoded = item.getItemMeta().getPersistentDataContainer()
                    .get(packageKey, PersistentDataType.STRING);
            if (!packageId.toString().equals(encoded)) continue;
            if (item.getAmount() <= 1) player.getInventory().setItem(slot, null);
            else item.setAmount(item.getAmount() - 1);
            return;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }
}
