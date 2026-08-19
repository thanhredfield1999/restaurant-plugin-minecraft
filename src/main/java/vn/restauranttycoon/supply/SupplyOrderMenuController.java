package vn.restauranttycoon.supply;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.economy.InsufficientFundsException;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;

public final class SupplyOrderMenuController implements Listener, AutoCloseable {
    private static final List<Integer> INGREDIENT_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final int CONFIRM_SLOT = 22;
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final IngredientCatalog catalog;
    private final SupplyOrderMenuLifecycle lifecycle = new SupplyOrderMenuLifecycle();

    public SupplyOrderMenuController(JavaPlugin plugin, DatabaseManager database, IngredientCatalog catalog) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public boolean openFromCommand(Player player, String plotId) {
        if (database.state() != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + "Cơ sở dữ liệu chưa sẵn sàng.");
            return true;
        }
        UUID playerId = player.getUniqueId();
        SupplyOrderMenuLifecycle.Ticket ticket = lifecycle.open(playerId).orElse(null);
        if (ticket == null) return true;
        player.sendMessage(ChatColor.GRAY + "Đang kiểm tra quyền sở hữu và thiết lập nhập hàng...");
        CompletableFuture.supplyAsync(() -> preflight(plotId, playerId), database.executor())
                .whenComplete((result, error) -> lifecycle.dispatchIfCurrent(ticket, this::runSync, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        player.sendMessage(ChatColor.RED + "Không thể kiểm tra hệ thống nhập hàng.");
                    } else if (result.status() != SupplyOrderPreflight.Status.READY) {
                        player.sendMessage(ChatColor.RED + (result.status() == SupplyOrderPreflight.Status.NOT_OWNER
                                ? "Bạn không sở hữu nhà hàng này."
                                : "Thiết lập nhập hàng chưa hoàn chỉnh: " + String.join(", ", result.details())));
                    } else {
                        show(player, new OrderHolder(plotId, SupplyOrderMenuModel.empty(catalog), ticket));
                    }
                }));
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lifecycle.close(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        lifecycle.close();
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder raw = event.getView().getTopInventory().getHolder();
        if (raw instanceof OrderHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory() || holder.submitting) return;
            int index = INGREDIENT_SLOTS.indexOf(event.getSlot());
            if (index >= 0 && index < holder.model.entries().size()) {
                String sku = holder.model.entries().get(index).sku();
                int step = event.isShiftClick() ? 10 : 1;
                try {
                    holder.model = event.isRightClick()
                            ? holder.model.decrease(sku, step)
                            : holder.model.increase(sku, step);
                    show(player, holder);
                } catch (IllegalArgumentException exception) {
                    player.sendMessage(ChatColor.RED + "Số lượng đã chạm giới hạn của nguyên liệu.");
                }
            } else if (event.getSlot() == CONFIRM_SLOT) {
                try {
                    holder.model.selectedLines();
                    showConfirmation(player, holder);
                } catch (IllegalStateException exception) {
                    player.sendMessage(ChatColor.RED + "Hãy chọn ít nhất một nguyên liệu.");
                }
            }
        } else if (raw instanceof ConfirmHolder confirmation) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            if (event.getSlot() == 11) submit(player, confirmation.order);
            if (event.getSlot() == 15) show(player, confirmation.order);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof OrderHolder || holder instanceof ConfirmHolder) {
            event.setCancelled(true);
        }
    }

    private void submit(Player player, OrderHolder holder) {
        if (holder.submitting) return;
        holder.submitting = true;
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Đang gửi đơn và thanh toán...");
        UUID operationId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SupplierOrder order = SupplierOrder.draft(player.getUniqueId(), player.getUniqueId(), catalog);
        for (SupplyOrderMenuEntry line : holder.model.selectedLines()) order = order.addLine(line.sku(), line.quantity());
        SupplierOrder submitted = order.submit(operationId);
        CompletableFuture.supplyAsync(() -> capture(holder.plotId, orderId, submitted, operationId), database.executor())
                .whenComplete((receipt, error) -> lifecycle.dispatchIfCurrent(holder.ticket, this::runSync, () -> {
                    holder.submitting = false;
                    if (!player.isOnline()) return;
                    if (error != null) {
                        Throwable root = root(error);
                        player.sendMessage(ChatColor.RED + (root instanceof InsufficientFundsException
                                ? "Bạn không đủ tiền để đặt đơn này."
                                : root instanceof SupplyOrderAuthorizationException
                                ? "Quyền sở hữu nhà hàng đã thay đổi; đơn không được tạo và không bị trừ tiền."
                                : "Đặt hàng thất bại; giao dịch đã được hoàn tác."));
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + "Đặt hàng thành công. Mã đơn: " + receipt.orderId());
                    player.sendMessage(ChatColor.GREEN + "Số dư còn lại: " + receipt.balance().units());
                }));
    }

    private void show(Player player, OrderHolder holder) {
        Inventory inventory = Bukkit.createInventory(holder, 27, "Đặt nguyên liệu - " + holder.plotId);
        holder.inventory = inventory;
        fill(inventory);
        List<SupplyOrderMenuEntry> entries = holder.model.entries();
        for (int i = 0; i < entries.size(); i++) {
            SupplyOrderMenuEntry entry = entries.get(i);
            inventory.setItem(INGREDIENT_SLOTS.get(i), item(Material.CHEST,
                    ChatColor.GOLD + entry.displayName(), List.of(
                            "SKU: " + entry.sku(), "Đơn vị: " + entry.unit(),
                            "Đơn giá: " + entry.unitPrice().units(), "Số lượng: " + entry.quantity(),
                            "Trái: +1 | Phải: -1", "Shift: thay đổi 10")));
        }
        inventory.setItem(CONFIRM_SLOT, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "Xác nhận đơn",
                List.of("Tổng tiền: " + holder.model.total().units(), "Nhấp để xem xác nhận thanh toán")));
        player.openInventory(inventory);
    }

    private void showConfirmation(Player player, OrderHolder order) {
        ConfirmHolder holder = new ConfirmHolder(order);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Xác nhận thanh toán");
        holder.inventory = inventory;
        fill(inventory);
        inventory.setItem(11, item(Material.LIME_CONCRETE, ChatColor.GREEN + "Thanh toán " + order.model.total().units(),
                List.of("Đơn sẽ được ghi bền vững trước khi báo thành công.")));
        inventory.setItem(15, item(Material.RED_CONCRETE, ChatColor.RED + "Quay lại", List.of("Không trừ tiền")));
        player.openInventory(inventory);
    }

    private SupplyOrderPreflight preflight(String plotId, UUID playerId) {
        try { return new SupplyOrderPreflightService(database.requireDataSource()).check(plotId, playerId); }
        catch (SQLException exception) { throw new CompletionException(exception); }
    }

    private SupplyOrderReceipt capture(String plotId, UUID orderId, SupplierOrder order, UUID operationId) {
        try { return new SupplyOrderRepository(database.requireDataSource())
                .captureAuthorized(plotId, orderId, order, new OperationKey(operationId), catalog); }
        catch (SQLException exception) { throw new CompletionException(exception); }
    }

    private void fill(Inventory inventory) {
        ItemStack pane = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane.clone());
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void runSync(Runnable task) { Bukkit.getScheduler().runTask(plugin, task); }
    private static Throwable root(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    private static final class OrderHolder implements InventoryHolder {
        private final String plotId;
        private final SupplyOrderMenuLifecycle.Ticket ticket;
        private SupplyOrderMenuModel model;
        private Inventory inventory;
        private boolean submitting;
        private OrderHolder(
                String plotId,
                SupplyOrderMenuModel model,
                SupplyOrderMenuLifecycle.Ticket ticket
        ) {
            this.plotId = plotId;
            this.model = model;
            this.ticket = ticket;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    private static final class ConfirmHolder implements InventoryHolder {
        private final OrderHolder order;
        private Inventory inventory;
        private ConfirmHolder(OrderHolder order) { this.order = order; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
