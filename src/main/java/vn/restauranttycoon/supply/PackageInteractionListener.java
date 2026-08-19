package vn.restauranttycoon.supply;

import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;

public final class PackageInteractionListener implements Listener {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final NamespacedKey packageKey;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public PackageInteractionListener(JavaPlugin plugin, DatabaseManager database, NamespacedKey packageKey) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.packageKey = Objects.requireNonNull(packageKey, "packageKey");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        String encoded = event.getRightClicked().getPersistentDataContainer()
                .get(packageKey, PersistentDataType.STRING);
        if (encoded == null) return;
        event.setCancelled(true);
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
        ItemStack token = createPackageToken(packageId);
        String operationKey = player.getUniqueId() + ":" + packageId;
        if (!inFlight.add(operationKey)) {
            player.sendMessage(ChatColor.YELLOW + "Package đang được xử lý.");
            return;
        }
        UUID operationId = UUID.nameUUIDFromBytes(operationKey.getBytes(StandardCharsets.UTF_8));
        if (hasPackageToken(player, packageId)) {
            inFlight.remove(operationKey);
            player.sendMessage(ChatColor.YELLOW + "Bạn đã có token package này trong túi đồ.");
            return;
        }
        if (player.getInventory().firstEmpty() < 0) {
            inFlight.remove(operationKey);
            player.sendMessage(ChatColor.RED + "Túi đồ đầy; package chưa thể nhận.");
            return;
        }
        java.util.Map<Integer, ItemStack> tokenOverflow = player.getInventory().addItem(token);
        if (!tokenOverflow.isEmpty()) {
            inFlight.remove(operationKey);
            player.sendMessage(ChatColor.RED + "Túi đồ đầy; package chưa thể nhận.");
            return;
        }
        player.sendMessage(ChatColor.GRAY + "Đang nhận package...");
        database.executor().execute(() -> {
            try {
                new SupplyFulfillmentRepository(database.requireDataSource()).handoffPackageAuthorizedForOrderPlayer(
                        packageId, player.getUniqueId(), operationId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    event.getRightClicked().remove();
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.GREEN + "Đã nhận package. Hãy mang vào kho để nhập hàng.");
                    }
                });
            } catch (SQLException | RuntimeException exception) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.getInventory().removeItem(token);
                    if (player.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Không thể nhận package: " + rootMessage(exception));
                    }
                });
            } finally {
                inFlight.remove(operationKey);
            }
        });
    }

    private boolean hasPackageToken(Player player, UUID packageId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String encoded = item.getItemMeta().getPersistentDataContainer()
                    .get(packageKey, PersistentDataType.STRING);
            if (packageId.toString().equals(encoded)) return true;
        }
        return false;
    }

    private ItemStack createPackageToken(UUID packageId) {
        ItemStack token = new ItemStack(Material.CHEST);
        ItemMeta meta = token.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Supply Package");
        meta.getPersistentDataContainer().set(packageKey, PersistentDataType.STRING, packageId.toString());
        token.setItemMeta(meta);
        return token;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }
}
