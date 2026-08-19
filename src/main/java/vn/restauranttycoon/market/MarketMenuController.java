package vn.restauranttycoon.market;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;
import vn.restauranttycoon.plot.PlotAssignmentRepository;
import vn.restauranttycoon.supply.IngredientCatalog;

public final class MarketMenuController implements Listener, AutoCloseable {
    private static final List<Integer> ITEM_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16);
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final IngredientCatalog catalog;

    public MarketMenuController(JavaPlugin plugin, DatabaseManager database, IngredientCatalog catalog) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public boolean openFromSupplier(Player player) {
        if (database.state() != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + "Cơ sở dữ liệu chưa sẵn sàng.");
            return true;
        }
        player.sendMessage(ChatColor.GRAY + "Đang tìm nhà hàng của bạn...");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return new PlotAssignmentRepository(database.requireDataSource())
                        .findByAccount(player.getUniqueId())
                        .map(vn.restauranttycoon.plot.PlotAssignment::plotId)
                        .orElse(null);
            } catch (java.sql.SQLException exception) {
                throw new CompletionException(exception);
            }
        }, database.executor()).whenComplete((plotId, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) {
                player.sendMessage(ChatColor.RED + "Không thể xác định nhà hàng.");
            } else if (plotId == null) {
                player.sendMessage(ChatColor.RED + "Bạn chưa sở hữu nhà hàng.");
            } else {
                openFromCommand(player, plotId);
            }
        }));
        return true;
    }

    public boolean openFromCommand(Player player) {
        return openFromCommand(player, null);
    }

    public boolean openFromCommand(Player player, String plotId) {
        if (database.state() != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + "Cơ sở dữ liệu chưa sẵn sàng.");
            return true;
        }
        player.sendMessage(ChatColor.GRAY + "Đang tải giá chợ từ PostgreSQL...");
        CompletableMarketLoad.load(database, catalog).whenComplete((model, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        player.sendMessage(ChatColor.RED + "Không thể tải giá thị trường.");
                        return;
                    }
                    show(player, model, plotId);
                }));
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketHolder holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (holder.confirmation) {
            if (event.getSlot() == 11) submit(player, holder);
            if (event.getSlot() == 15) {
                holder.confirmation = false;
                show(player, holder.model, holder.plotId);
            }
            return;
        }
        int index = ITEM_SLOTS.indexOf(event.getSlot());
        if (index >= 0 && index < holder.model.entries().size()) {
            String sku = holder.model.entries().get(index).ingredient().sku();
            int amount = event.isShiftClick() ? 10 : 1;
            try {
                holder.model = event.isRightClick()
                        ? holder.model.decrease(sku, amount)
                        : holder.model.increase(sku, amount);
                show(player, holder.model, holder.plotId);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(ChatColor.RED + "Số lượng đã chạm giới hạn.");
            }
        } else if (event.getSlot() == 22) {
            if (holder.model.selectedItemCount() == 0) {
                player.sendMessage(ChatColor.RED + "Hãy chọn ít nhất một nguyên liệu.");
            } else if (holder.plotId == null) {
                player.sendMessage(ChatColor.YELLOW + "Dùng /restaurant market <plotId> để xác nhận đơn.");
            } else {
                showConfirmation(player, holder);
            }
        } else if (event.getSlot() == 11 && holder.confirmation) {
            submit(player, holder);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // InventoryHolder không giữ session hoặc task; đóng tự nhiên theo player quit.
    }

    @Override
    public void close() {
        // Không có worker riêng để hủy.
    }

    private void show(Player player, MarketMenuModel model, String plotId) {
        MarketHolder holder = new MarketHolder(model, plotId);
        Inventory inventory = Bukkit.createInventory(holder, 27, "Chợ đầu mối");
        holder.inventory = inventory;
        ItemStack pane = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane.clone());
        List<MarketMenuEntry> entries = model.entries();
        for (int i = 0; i < entries.size(); i++) {
            MarketMenuEntry entry = entries.get(i);
            MarketPrice price = entry.price();
            String color = priceDeltaColor(entry);
            inventory.setItem(ITEM_SLOTS.get(i), item(Material.CHEST,
                    ChatColor.GOLD + entry.ingredient().displayName(), List.of(
                            "SKU: " + entry.ingredient().sku(),
                            "Giá hiện tại: " + color + price.unitPrice(),
                            "Giá cơ bản: " + price.basePrice(),
                            "Xu hướng: " + color + entry.trend(),
                            "Nhu cầu cycle: " + price.quantityDemanded(),
                            "Nguồn cung cycle: " + price.quantitySupplied(),
                            "Số lượng: " + holder.model.quantity(entry.ingredient().sku()),
                            "Trái: +1 | Phải: -1 | Shift: 10",
                            "Đặt hàng: xem trước, chưa thanh toán")));
        }
        inventory.setItem(22, item(Material.EMERALD_BLOCK, ChatColor.GREEN + "Xem trước đơn", List.of(
                "Số lượng: " + holder.model.selectedItemCount(),
                "Tổng: " + holder.model.total().units(),
                "Chưa trừ tiền")));
        player.openInventory(inventory);
    }

    private void showConfirmation(Player player, MarketHolder holder) {
        holder.confirmation = true;
        Inventory inventory = Bukkit.createInventory(holder, 27, "Xác nhận đơn chợ");
        holder.inventory = inventory;
        ItemStack pane = item(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane.clone());
        inventory.setItem(11, item(Material.LIME_CONCRETE, ChatColor.GREEN + "Thanh toán " + holder.model.total().units(),
                List.of("Plot: " + holder.plotId, "Giá sẽ snapshot lại trong transaction", "Nhấp để xác nhận")));
        inventory.setItem(15, item(Material.RED_CONCRETE, ChatColor.RED + "Quay lại", List.of("Không trừ tiền")));
        player.openInventory(inventory);
    }

    private void submit(Player player, MarketHolder holder) {
        if (holder.submitting) return;
        holder.submitting = true;
        player.closeInventory();
        java.util.UUID operation = java.util.UUID.randomUUID();
        java.util.UUID orderId = java.util.UUID.randomUUID();
        vn.restauranttycoon.supply.SupplierOrder order =
                vn.restauranttycoon.supply.SupplierOrder.draft(player.getUniqueId(), player.getUniqueId(), catalog);
        for (MarketMenuEntry entry : holder.model.entries()) {
            int quantity = holder.model.quantity(entry.ingredient().sku());
            if (quantity > 0) order = order.addLine(entry.ingredient().sku(), quantity);
        }
        vn.restauranttycoon.supply.SupplierOrder submitted = order.submit(operation);
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try {
                return new vn.restauranttycoon.supply.SupplyOrderRepository(database.requireDataSource())
                        .captureMarketAuthorized(holder.plotId, orderId, submitted,
                                new vn.restauranttycoon.economy.OperationKey(operation), catalog, Instant.now());
            } catch (java.sql.SQLException exception) {
                throw new CompletionException(exception);
            }
        }, database.executor()).whenComplete((receipt, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null) player.sendMessage(ChatColor.RED + "Thanh toán thất bại; transaction đã rollback.");
            else player.sendMessage(ChatColor.GREEN + "Đặt hàng thành công: " + receipt.orderId());
        }));
    }

    private static String priceDeltaColor(MarketMenuEntry entry) {
        return entry.priceDeltaFromBase() > 0 ? ChatColor.RED.toString()
                : entry.priceDeltaFromBase() < 0 ? ChatColor.GREEN.toString() : ChatColor.YELLOW.toString();
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final class MarketHolder implements InventoryHolder {
        private MarketMenuModel model;
        private final String plotId;
        private boolean confirmation;
        private boolean submitting;
        private Inventory inventory;
        private MarketHolder(MarketMenuModel model, String plotId) { this.model = model; this.plotId = plotId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class CompletableMarketLoad {
        private static java.util.concurrent.CompletableFuture<MarketMenuModel> load(
                DatabaseManager database, IngredientCatalog catalog) {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    MarketRepository repository = new MarketRepository(database.requireDataSource());
                    Map<String, Long> bases = catalog.definitions().stream().collect(
                            java.util.stream.Collectors.toMap(d -> d.sku(), d -> d.unitPrice().units()));
                    Instant now = Instant.now();
                    repository.ensureOpenCycle(bases, now, Duration.ofMinutes(10));
                    List<MarketPrice> prices = catalog.definitions().stream()
                            .map(d -> {
                                try { return repository.findPrice(d.sku(), now); }
                                catch (java.sql.SQLException exception) { throw new CompletionException(exception); }
                            }).toList();
                    return MarketMenuModel.from(catalog, prices);
                } catch (java.sql.SQLException exception) {
                    throw new CompletionException(exception);
                }
            }, database.executor());
        }
    }
}
