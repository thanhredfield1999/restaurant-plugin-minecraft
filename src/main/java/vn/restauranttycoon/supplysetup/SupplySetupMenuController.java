package vn.restauranttycoon.supplysetup;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import vn.restauranttycoon.persistence.DatabaseManager;
import vn.restauranttycoon.persistence.DatabaseState;

public final class SupplySetupMenuController implements Listener {
    private static final Map<SupplySetupPointType, Integer> SLOTS = Map.of(
            SupplySetupPointType.ORDER_DESK, 11,
            SupplySetupPointType.SUPPLIER_SPAWN, 15,
            SupplySetupPointType.DELIVERY_ENTRY, 10,
            SupplySetupPointType.DELIVERY_STOP, 11,
            SupplySetupPointType.UNLOAD_POINT, 12,
            SupplySetupPointType.WAREHOUSE_ENTRY, 14,
            SupplySetupPointType.DELIVERY_EXIT, 15,
            SupplySetupPointType.DELIVERY_DESPAWN, 16);

    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final Set<String> restaurantIds;
    private final BiConsumer<Player, String> routeOpener;
    private final SupplySetupMessages messages;
    private final SupplySetupPointPresenter presenter;
    private final SupplySetupMenuLayout layout = new SupplySetupMenuLayout();
    private final Logger logger;

    public SupplySetupMenuController(
            JavaPlugin plugin,
            DatabaseManager database,
            Set<String> restaurantIds,
            BiConsumer<Player, String> routeOpener
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.database = Objects.requireNonNull(database, "database");
        this.restaurantIds = Set.copyOf(restaurantIds);
        this.routeOpener = Objects.requireNonNull(routeOpener, "routeOpener");
        this.messages = new BukkitSupplySetupMessages(plugin);
        this.presenter = new SupplySetupPointPresenter(messages);
        this.logger = plugin.getLogger();
    }

    public boolean openFromCommand(Player player, String target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        if (!player.hasPermission("restauranttycoon.admin.setup")) {
            player.sendMessage(ChatColor.RED + messages.text("menu.no-permission"));
            return true;
        }
        if (database.state() != DatabaseState.READY) {
            player.sendMessage(ChatColor.RED + messages.text("menu.database-unavailable"));
            return true;
        }
        SupplySetupOwner owner;
        if (target.equalsIgnoreCase("central")) {
            owner = SupplySetupOwner.centralSupplier();
        } else if (restaurantIds.contains(target)) {
            owner = SupplySetupOwner.restaurant(target);
        } else {
            player.sendMessage(ChatColor.RED + messages.text(
                    "menu.unknown-restaurant", Map.of("restaurant", target)));
            return true;
        }
        player.sendMessage(ChatColor.GRAY + messages.text("menu.loading"));
        open(player, owner);
        return true;
    }

    private void open(Player player, SupplySetupOwner owner) {
        CompletableFuture.supplyAsync(() -> findAll(owner), database.executor())
                .whenComplete((points, error) -> runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        fail(player, "load", error);
                        return;
                    }
                    showMenu(player, owner, points);
                }));
    }

    private List<SupplySetupPoint> findAll(SupplySetupOwner owner) {
        try {
            return repository().findAll(owner);
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private void showMenu(Player player, SupplySetupOwner owner, List<SupplySetupPoint> points) {
        Map<SupplySetupPointType, SupplySetupPoint> byType = points.stream()
                .collect(Collectors.toMap(SupplySetupPoint::type, point -> point));
        MenuHolder holder = new MenuHolder(owner);
        String title = owner.scope() == SupplySetupScope.CENTRAL_SUPPLIER
                ? messages.text("menu.central-title")
                : messages.text("menu.restaurant-title", Map.of("restaurant", owner.ownerId()));
        Inventory inventory = Bukkit.createInventory(holder, SupplySetupMenuLayout.INVENTORY_SIZE, title);
        holder.attach(inventory);
        fillBlankSlots(inventory, layout.blankSlots(owner.scope()));
        for (SupplySetupPointType type : SupplySetupPointType.values()) {
            if (type.scope() != owner.scope()) {
                continue;
            }
            SupplySetupPointView view = presenter.present(type, Optional.ofNullable(byType.get(type)));
            inventory.setItem(SLOTS.get(type), pointItem(view));
        }
        if (owner.scope() == SupplySetupScope.RESTAURANT) {
            inventory.setItem(SupplySetupMenuLayout.ROUTE_SLOT, namedItem(
                    Material.MINECART,
                    ChatColor.GREEN + messages.text("route.open-name"),
                    splitLore(messages.text("route.open-lore"), ChatColor.GRAY)));
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MenuHolder menu) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            if (menu.owner().scope() == SupplySetupScope.RESTAURANT
                    && event.getSlot() == SupplySetupMenuLayout.ROUTE_SLOT) {
                routeOpener.accept(player, menu.owner().ownerId());
                return;
            }
            SupplySetupPointType type = typeAt(menu.owner(), event.getSlot());
            if (type == null) {
                return;
            }
            if (event.isShiftClick() && event.isRightClick()) {
                showDeleteConfirmation(player, menu.owner(), type);
            } else if (event.isLeftClick()) {
                saveCurrentPosition(player, menu.owner(), type);
            } else if (event.isRightClick()) {
                teleport(player, menu.owner(), type);
            }
        } else if (holder instanceof ConfirmDeleteHolder confirmation) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }
            if (event.getSlot() == 11) {
                delete(player, confirmation.owner(), confirmation.type());
            } else if (event.getSlot() == 15) {
                open(player, confirmation.owner());
            }
        }
    }

    private void saveCurrentPosition(
            Player player,
            SupplySetupOwner owner,
            SupplySetupPointType type
    ) {
        Location location = player.getLocation();
        SupplySetupPoint point = new SupplySetupPoint(owner, type, new SupplySetupPosition(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()));
        CompletableFuture.runAsync(() -> upsert(point), database.executor())
                .whenComplete((ignored, error) -> runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        fail(player, "save", error);
                        return;
                    }
                    player.sendMessage(ChatColor.GREEN + messages.text(
                            "feedback.saved", Map.of("point", pointName(type))));
                    open(player, owner);
                }));
    }

    private void teleport(Player player, SupplySetupOwner owner, SupplySetupPointType type) {
        CompletableFuture.supplyAsync(() -> find(owner, type), database.executor())
                .whenComplete((point, error) -> runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        fail(player, "teleport lookup", error);
                        return;
                    }
                    if (point.isEmpty()) {
                        player.sendMessage(ChatColor.YELLOW + messages.text("feedback.unset"));
                        return;
                    }
                    SupplySetupPosition position = point.get().position();
                    World world = Bukkit.getWorld(position.worldName());
                    if (world == null) {
                        player.sendMessage(ChatColor.RED + messages.text(
                                "feedback.world-unavailable",
                                Map.of("world", position.worldName())));
                        return;
                    }
                    player.teleport(new Location(
                            world,
                            position.x(),
                            position.y(),
                            position.z(),
                            position.yaw(),
                            position.pitch()));
                }));
    }

    private void showDeleteConfirmation(
            Player player,
            SupplySetupOwner owner,
            SupplySetupPointType type
    ) {
        ConfirmDeleteHolder holder = new ConfirmDeleteHolder(owner, type);
        Inventory inventory = Bukkit.createInventory(
                holder, SupplySetupMenuLayout.INVENTORY_SIZE, messages.text("confirm.title"));
        holder.attach(inventory);
        fillBlankSlots(inventory, layout.confirmBlankSlots());
        inventory.setItem(SupplySetupMenuLayout.CONFIRM_DELETE_SLOT, namedItem(
                Material.RED_CONCRETE,
                ChatColor.RED + messages.text("confirm.delete-name", Map.of("point", pointName(type))),
                splitLore(messages.text("confirm.delete-lore"), ChatColor.RED)));
        inventory.setItem(SupplySetupMenuLayout.CONFIRM_CANCEL_SLOT, namedItem(
                Material.LIME_CONCRETE,
                ChatColor.GREEN + messages.text("confirm.cancel-name"),
                splitLore(messages.text("confirm.cancel-lore"), ChatColor.GRAY)));
        player.openInventory(inventory);
    }

    private void delete(Player player, SupplySetupOwner owner, SupplySetupPointType type) {
        CompletableFuture.supplyAsync(() -> deleteStored(owner, type), database.executor())
                .whenComplete((deleted, error) -> runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (error != null) {
                        fail(player, "delete", error);
                        return;
                    }
                    if (deleted) {
                        player.sendMessage(ChatColor.GREEN + messages.text(
                                "feedback.deleted", Map.of("point", pointName(type))));
                    } else {
                        player.sendMessage(ChatColor.YELLOW + messages.text("feedback.unset"));
                    }
                    open(player, owner);
                }));
    }

    private void upsert(SupplySetupPoint point) {
        try {
            repository().upsert(point);
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private Optional<SupplySetupPoint> find(
            SupplySetupOwner owner,
            SupplySetupPointType type
    ) {
        try {
            return repository().find(owner, type);
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private boolean deleteStored(SupplySetupOwner owner, SupplySetupPointType type) {
        try {
            return repository().delete(owner, type);
        } catch (SQLException exception) {
            throw new CompletionException(exception);
        }
    }

    private SupplySetupPointRepository repository() {
        return new SupplySetupPointRepository(database.requireDataSource());
    }

    private void fillBlankSlots(Inventory inventory, Set<Integer> blankSlots) {
        ItemStack pane = namedItem(Material.WHITE_STAINED_GLASS_PANE, " ", List.of());
        for (int slot : blankSlots) {
            inventory.setItem(slot, pane.clone());
        }
    }

    private ItemStack pointItem(SupplySetupPointView view) {
        Material material = view.status() == SupplySetupPointStatus.CONFIGURED
                ? Material.LIME_CONCRETE
                : Material.GRAY_CONCRETE;
        ChatColor color = view.status() == SupplySetupPointStatus.CONFIGURED
                ? ChatColor.GREEN
                : ChatColor.GRAY;
        return namedItem(material, color + view.title(), view.lore().stream()
                .map(line -> line.isEmpty() ? "" : ChatColor.GRAY + line)
                .toList());
    }

    private static ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static List<String> splitLore(String text, ChatColor color) {
        return text.lines().flatMap(line -> List.of(line.split("\\|", -1)).stream())
                .map(line -> color + line)
                .toList();
    }

    private SupplySetupPointType typeAt(SupplySetupOwner owner, int slot) {
        return SLOTS.entrySet().stream()
                .filter(entry -> entry.getValue() == slot && entry.getKey().scope() == owner.scope())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private String pointName(SupplySetupPointType type) {
        return messages.text("point." + type.name() + ".name");
    }

    private void fail(Player player, String operation, Throwable error) {
        player.sendMessage(ChatColor.RED + messages.text("feedback.operation-failed"));
        logger.warning("Supply setup " + operation + " failed for " + player.getUniqueId()
                + ": " + rootMessage(error));
    }

    private void runSync(Runnable action) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, action);
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static final class MenuHolder implements InventoryHolder {
        private final SupplySetupOwner owner;
        private Inventory inventory;

        private MenuHolder(SupplySetupOwner owner) {
            this.owner = owner;
        }

        private SupplySetupOwner owner() {
            return owner;
        }

        private void attach(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return Objects.requireNonNull(inventory, "inventory");
        }
    }

    private static final class ConfirmDeleteHolder implements InventoryHolder {
        private final SupplySetupOwner owner;
        private final SupplySetupPointType type;
        private Inventory inventory;

        private ConfirmDeleteHolder(SupplySetupOwner owner, SupplySetupPointType type) {
            this.owner = owner;
            this.type = type;
        }

        private SupplySetupOwner owner() {
            return owner;
        }

        private SupplySetupPointType type() {
            return type;
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
