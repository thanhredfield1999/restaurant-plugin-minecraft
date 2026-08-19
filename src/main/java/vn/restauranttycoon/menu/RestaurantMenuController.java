package vn.restauranttycoon.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
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
import vn.restauranttycoon.economy.EconomyService;
import vn.restauranttycoon.lore.RestaurantLore;
import vn.restauranttycoon.persistence.DatabaseState;

public final class RestaurantMenuController implements Listener {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final Map<String, Integer> orderSlots;
    private final RestaurantMenuLayout layout;
    private final java.util.function.BiConsumer<Player, String> orderOpener;

    public RestaurantMenuController(
            JavaPlugin plugin,
            EconomyService economy,
            List<String> restaurantIds,
            java.util.function.BiConsumer<Player, String> orderOpener
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.layout = new RestaurantMenuLayout(restaurantIds);
        this.orderSlots = layout.orderSlots();
        this.orderOpener = Objects.requireNonNull(orderOpener, "orderOpener");
    }

    public boolean openFromCommand(Player player, DatabaseState databaseState) {
        if (databaseState != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + "Cơ sở dữ liệu chưa sẵn sàng.");
            return true;
        }
        MenuHolder holder = new MenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, layout.inventorySize(), "Nhà hàng của bạn");
        holder.attach(inventory);
        fill(inventory, layout.blankSlots());
        inventory.setItem(RestaurantMenuLayout.BALANCE_SLOT, item(Material.SUNFLOWER,
                ChatColor.GOLD + "Đang tải số dư...", List.of("Số dư nhà hàng của bạn.")));
        inventory.setItem(RestaurantMenuLayout.GUIDE_SLOT, item(Material.BOOK,
                ChatColor.AQUA + "Sổ vận hành", RestaurantLore.dashboardGuide()));
        for (Map.Entry<String, Integer> entry : orderSlots.entrySet()) {
            inventory.setItem(entry.getValue(), item(Material.CHEST,
                    ChatColor.GREEN + "Nhà hàng " + entry.getKey(), RestaurantLore.restaurantCard(entry.getKey())));
        }
        player.openInventory(inventory);
        economy.balance(player.getUniqueId()).whenComplete((balance, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
                return;
            }
            inventory.setItem(RestaurantMenuLayout.BALANCE_SLOT, error == null
                    ? item(Material.SUNFLOWER, ChatColor.GOLD + "Số dư: " + balance.units(),
                            List.of("Tiền dùng để mua nguyên liệu và nâng cấp."))
                    : item(Material.BARRIER, ChatColor.RED + "Không tải được số dư",
                            List.of("Thử lại bằng /restaurant khi cơ sở dữ liệu hoạt động.")));
        }));
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        orderSlots.entrySet().stream()
                .filter(entry -> entry.getValue() == event.getSlot())
                .findFirst()
                .ifPresent(entry -> orderOpener.accept(player, entry.getKey()));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    private static void fill(Inventory inventory, java.util.Set<Integer> slots) {
        ItemStack pane = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : slots) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID playerId;
        private Inventory inventory;

        private MenuHolder(UUID playerId) {
            this.playerId = playerId;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return Objects.requireNonNull(inventory, "inventory");
        }
    }
}
