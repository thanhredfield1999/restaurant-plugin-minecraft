package vn.restauranttycoon.supply;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.sql.DataSource;
import vn.restauranttycoon.market.MarketPrice;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.market.MarketRepository;
import vn.restauranttycoon.economy.EconomyRepository;
import vn.restauranttycoon.economy.LedgerResult;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.supplysetup.SupplySetupPointType;

public final class SupplyOrderRepository {
    private static final String PAYMENT_REASON = "SUPPLY_ORDER_PAYMENT";

    private final DataSource dataSource;
    private final EconomyRepository economy;
    private final MarketRepository market;

    public SupplyOrderRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.economy = new EconomyRepository(dataSource);
        this.market = new MarketRepository(dataSource);
    }

    public SupplyOrderReceipt capture(
            UUID orderId,
            SupplierOrder order,
            OperationKey captureOperation
    ) throws SQLException {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(captureOperation, "captureOperation");
        if (order.state() != SupplierOrderState.SUBMITTED) {
            throw new IllegalArgumentException("Order must be submitted before payment capture");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LedgerResult payment = economy.apply(
                        connection,
                        order.playerId(),
                        captureOperation,
                        Math.negateExact(order.total().units()),
                        PAYMENT_REASON);
                if (!payment.duplicate()) {
                    insertOrder(connection, orderId, order, captureOperation.value());
                    insertLines(connection, orderId, order);
                    insertPayment(connection, orderId, order, captureOperation.value());
                    createFulfillment(connection, orderId, order.restaurantId());
                } else {
                    requireExistingPayment(connection, orderId, captureOperation.value(), order.total().units());
                }
                connection.commit();
                return new SupplyOrderReceipt(orderId, payment.balance(), payment.duplicate());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SupplyOrderReceipt captureMarket(
            UUID orderId, SupplierOrder submittedOrder, OperationKey captureOperation,
            IngredientCatalog authoritativeCatalog, Instant now
    ) throws SQLException {
        requireAuthoritativeSnapshot(submittedOrder, authoritativeCatalog);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                SupplyOrderReceipt receipt = captureMarket(connection, orderId, submittedOrder, captureOperation, now);
                connection.commit();
                return receipt;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SupplyOrderReceipt captureMarketAuthorized(
            String plotId, UUID orderId, SupplierOrder submittedOrder, OperationKey captureOperation,
            IngredientCatalog authoritativeCatalog, Instant now
    ) throws SQLException {
        if (plotId == null || !plotId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid plot ID");
        }
        Objects.requireNonNull(submittedOrder, "submittedOrder");
        Objects.requireNonNull(captureOperation, "captureOperation");
        Objects.requireNonNull(now, "now");
        requireAuthoritativeSnapshot(submittedOrder, authoritativeCatalog);
        if (!submittedOrder.restaurantId().equals(submittedOrder.playerId())) {
            throw new SupplyOrderAuthorizationException("Restaurant identity does not match the ordering player");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePlotOwnership(connection, plotId, submittedOrder.playerId());
                requireCompleteSetup(connection, plotId);
                SupplyOrderReceipt receipt = captureMarket(
                        connection, orderId, submittedOrder, captureOperation, now);
                connection.commit();
                return receipt;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private SupplyOrderReceipt captureMarket(
            Connection connection, UUID orderId, SupplierOrder submittedOrder,
            OperationKey captureOperation, Instant now
    ) throws SQLException {
        ExistingMarketOrder existing = findExistingMarketOrder(connection, captureOperation.value());
        if (existing != null) {
            requireExistingMarketLines(connection, existing.orderId(), submittedOrder);
            LedgerResult payment = economy.apply(connection, submittedOrder.playerId(), captureOperation,
                    Math.negateExact(existing.total()), PAYMENT_REASON);
            return new SupplyOrderReceipt(existing.orderId(), payment.balance(), true);
        }
        Map<String, MarketPrice> prices = market.lockPrices(connection,
                submittedOrder.lines().stream().map(SupplierOrderLine::sku).toList(), now);
        Map<String, CurrencyAmount> amounts = prices.entrySet().stream().collect(
                java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> new CurrencyAmount(entry.getValue().unitPrice())));
        SupplierOrder order = submittedOrder.repriceMarket(amounts);
        for (SupplierOrderLine line : order.lines()) {
            UUID demandOperation = UUID.nameUUIDFromBytes(
                    (captureOperation.value() + ":" + line.sku()).getBytes(StandardCharsets.UTF_8));
            market.recordPurchase(connection, new OperationKey(demandOperation),
                    line.sku(), line.quantity(), now);
        }
        LedgerResult payment = economy.apply(connection, order.playerId(), captureOperation,
                Math.negateExact(order.total().units()), PAYMENT_REASON);
        if (!payment.duplicate()) {
            insertOrder(connection, orderId, order, captureOperation.value());
            insertMarketLines(connection, orderId, order, prices);
            insertPayment(connection, orderId, order, captureOperation.value());
            createFulfillment(connection, orderId, order.restaurantId());
        } else {
            requireExistingOrder(connection, orderId, order, captureOperation.value());
        }
        return new SupplyOrderReceipt(orderId, payment.balance(), payment.duplicate());
    }

    public SupplyOrderReceipt captureAuthorized(
            String plotId,
            UUID orderId,
            SupplierOrder order,
            OperationKey captureOperation,
            IngredientCatalog authoritativeCatalog
    ) throws SQLException {
        if (plotId == null || !plotId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid plot ID");
        }
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(captureOperation, "captureOperation");
        Objects.requireNonNull(authoritativeCatalog, "authoritativeCatalog");
        if (order.state() != SupplierOrderState.SUBMITTED) {
            throw new IllegalArgumentException("Order must be submitted before payment capture");
        }
        if (!order.restaurantId().equals(order.playerId())) {
            throw new SupplyOrderAuthorizationException(
                    "Restaurant identity does not match the ordering player");
        }
        requireAuthoritativeSnapshot(order, authoritativeCatalog);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requirePlotOwnership(connection, plotId, order.playerId());
                requireCompleteSetup(connection, plotId);
                SupplyOrderReceipt receipt = capture(connection, orderId, order, captureOperation);
                connection.commit();
                return receipt;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private SupplyOrderReceipt capture(
            Connection connection,
            UUID orderId,
            SupplierOrder order,
            OperationKey captureOperation
    ) throws SQLException {
        LedgerResult payment = economy.apply(
                connection,
                order.playerId(),
                captureOperation,
                Math.negateExact(order.total().units()),
                PAYMENT_REASON);
        if (!payment.duplicate()) {
            insertOrder(connection, orderId, order, captureOperation.value());
            insertLines(connection, orderId, order);
            insertPayment(connection, orderId, order, captureOperation.value());
            createFulfillment(connection, orderId, order.restaurantId());
        } else {
            requireExistingOrder(connection, orderId, order, captureOperation.value());
        }
        return new SupplyOrderReceipt(orderId, payment.balance(), payment.duplicate());
    }

    private void requirePlotOwnership(Connection connection, String plotId, UUID playerId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT account_id FROM plot_assignments WHERE plot_id = ? FOR UPDATE")) {
            statement.setString(1, plotId);
            try (var result = statement.executeQuery()) {
                if (!result.next() || !playerId.equals(result.getObject(1, UUID.class))) {
                    throw new SupplyOrderAuthorizationException(
                            "Plot is not assigned to the ordering player: " + plotId);
                }
            }
        }
    }

    private void requireCompleteSetup(Connection connection, String plotId) throws SQLException {
        Set<SupplySetupPointType> central = EnumSet.noneOf(SupplySetupPointType.class);
        Set<SupplySetupPointType> restaurant = EnumSet.noneOf(SupplySetupPointType.class);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT setup_scope, point_type FROM supply_setup_points "
                        + "WHERE (setup_scope = 'CENTRAL_SUPPLIER' AND owner_id = 'central_supplier') "
                        + "OR (setup_scope = 'RESTAURANT' AND owner_id = ?) FOR UPDATE")) {
            statement.setString(1, plotId);
            try (var result = statement.executeQuery()) {
                while (result.next()) {
                    SupplySetupPointType type = SupplySetupPointType.valueOf(result.getString(2));
                    if ("CENTRAL_SUPPLIER".equals(result.getString(1))) {
                        central.add(type);
                    } else {
                        restaurant.add(type);
                    }
                }
            }
        }
        Set<SupplySetupPointType> requiredCentral = EnumSet.of(
                SupplySetupPointType.ORDER_DESK, SupplySetupPointType.SUPPLIER_SPAWN);
        Set<SupplySetupPointType> requiredRestaurant = EnumSet.of(
                SupplySetupPointType.DELIVERY_ENTRY,
                SupplySetupPointType.DELIVERY_STOP,
                SupplySetupPointType.UNLOAD_POINT,
                SupplySetupPointType.WAREHOUSE_ENTRY,
                SupplySetupPointType.DELIVERY_EXIT,
                SupplySetupPointType.DELIVERY_DESPAWN);
        if (!central.containsAll(requiredCentral) || !restaurant.containsAll(requiredRestaurant)) {
            throw new SupplyOrderSetupIncompleteException(
                    "Supply setup became incomplete before payment capture: " + plotId);
        }
    }

    private void requireAuthoritativeSnapshot(SupplierOrder order, IngredientCatalog catalog) {
        if (order.catalogVersion() != catalog.version()) {
            throw new SupplyOrderOperationConflictException("Catalog version is not authoritative");
        }
        for (SupplierOrderLine line : order.lines()) {
            IngredientDefinition definition;
            try {
                definition = catalog.require(line.sku());
            } catch (IllegalArgumentException exception) {
                throw new SupplyOrderOperationConflictException("Unknown authoritative SKU: " + line.sku());
            }
            if (!definition.displayName().equals(line.displayName())
                    || definition.unit() != line.unit()
                    || !definition.unitPrice().equals(line.unitPrice())
                    || line.quantity() > definition.maxQuantity()) {
                throw new SupplyOrderOperationConflictException(
                        "Order line does not match the authoritative catalog: " + line.sku());
            }
        }
    }

    private void requireExistingOrder(
            Connection connection,
            UUID orderId,
            SupplierOrder order,
            UUID operationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT restaurant_id, player_id, catalog_version, total "
                        + "FROM supply_orders WHERE order_id = ? AND operation_id = ?")) {
            statement.setObject(1, orderId);
            statement.setObject(2, operationId);
            try (var result = statement.executeQuery()) {
                if (!result.next()
                        || !order.restaurantId().equals(result.getObject(1, UUID.class))
                        || !order.playerId().equals(result.getObject(2, UUID.class))
                        || order.catalogVersion() != result.getInt(3)
                        || order.total().units() != result.getLong(4)) {
                    throw new SupplyOrderOperationConflictException(
                            "Supply order operation does not match the existing order");
                }
            }
        }
        java.util.Map<String, SupplierOrderLine> expected = order.lines().stream().collect(
                java.util.stream.Collectors.toMap(SupplierOrderLine::sku, line -> line));
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sku, display_name, unit, quantity, unit_price "
                        + "FROM supply_order_lines WHERE order_id = ?")) {
            statement.setObject(1, orderId);
            try (var result = statement.executeQuery()) {
                int count = 0;
                while (result.next()) {
                    count++;
                    SupplierOrderLine line = expected.get(result.getString(1));
                    if (line == null
                            || !line.displayName().equals(result.getString(2))
                            || !line.unit().name().equals(result.getString(3))
                            || line.quantity() != result.getInt(4)
                            || line.unitPrice().units() != result.getLong(5)) {
                        throw new SupplyOrderOperationConflictException(
                                "Supply order lines do not match the existing order");
                    }
                }
                if (count != expected.size()) {
                    throw new SupplyOrderOperationConflictException(
                            "Supply order line count does not match the existing order");
                }
            }
        }
        requireExistingPayment(connection, orderId, operationId, order.total().units());
    }

    private void insertOrder(
            Connection connection, UUID orderId, SupplierOrder order, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_orders "
                        + "(order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'SUBMITTED')")) {
            statement.setObject(1, orderId);
            statement.setObject(2, operationId);
            statement.setObject(3, order.restaurantId());
            statement.setObject(4, order.playerId());
            statement.setInt(5, order.catalogVersion());
            statement.setLong(6, order.total().units());
            statement.executeUpdate();
        }
    }

    private void insertLines(Connection connection, UUID orderId, SupplierOrder order) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_order_lines "
                        + "(order_id, sku, display_name, unit, quantity, unit_price) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (SupplierOrderLine line : order.lines()) {
                statement.setObject(1, orderId);
                statement.setString(2, line.sku());
                statement.setString(3, line.displayName());
                statement.setString(4, line.unit().name());
                statement.setInt(5, line.quantity());
                statement.setLong(6, line.unitPrice().units());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private ExistingMarketOrder findExistingMarketOrder(Connection connection, UUID operationId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT order_id, total FROM supply_orders WHERE operation_id = ? FOR UPDATE")) {
            statement.setObject(1, operationId);
            try (var result = statement.executeQuery()) {
                return result.next()
                        ? new ExistingMarketOrder(result.getObject(1, UUID.class), result.getLong(2))
                        : null;
            }
        }
    }

    private void requireExistingMarketLines(
            Connection connection, UUID orderId, SupplierOrder submittedOrder) throws SQLException {
        Map<String, Integer> expected = submittedOrder.lines().stream().collect(
                java.util.stream.Collectors.toMap(SupplierOrderLine::sku, SupplierOrderLine::quantity));
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT sku, quantity, market_cycle_id, price_snapshot FROM supply_order_lines WHERE order_id = ?")) {
            statement.setObject(1, orderId);
            try (var result = statement.executeQuery()) {
                int count = 0;
                while (result.next()) {
                    count++;
                    Integer quantity = expected.get(result.getString(1));
                    if (quantity == null || quantity != result.getInt(2)
                            || result.getObject(3) == null || result.getLong(4) <= 0) {
                        throw new SupplyOrderOperationConflictException(
                                "Market retry does not match existing immutable snapshot");
                    }
                }
                if (count != expected.size()) {
                    throw new SupplyOrderOperationConflictException(
                            "Market retry line count does not match existing order");
                }
            }
        }
    }

    private record ExistingMarketOrder(UUID orderId, long total) {
    }

    private void insertMarketLines(
            Connection connection,
            UUID orderId,
            SupplierOrder order,
            Map<String, MarketPrice> prices
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_order_lines "
                        + "(order_id, sku, display_name, unit, quantity, unit_price, market_cycle_id, price_snapshot) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (SupplierOrderLine line : order.lines()) {
                MarketPrice price = prices.get(line.sku());
                statement.setObject(1, orderId);
                statement.setString(2, line.sku());
                statement.setString(3, line.displayName());
                statement.setString(4, line.unit().name());
                statement.setInt(5, line.quantity());
                statement.setLong(6, price.unitPrice());
                statement.setObject(7, price.cycleId());
                statement.setLong(8, price.unitPrice());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertPayment(
            Connection connection, UUID orderId, SupplierOrder order, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_payments "
                        + "(order_id, amount, state, capture_operation_id) VALUES (?, ?, 'CAPTURED', ?)")) {
            statement.setObject(1, orderId);
            statement.setLong(2, order.total().units());
            statement.setObject(3, operationId);
            statement.executeUpdate();
        }
    }

    private void requireExistingPayment(
            Connection connection, UUID orderId, UUID operationId, long amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT amount FROM supply_payments WHERE order_id = ? AND capture_operation_id = ?")) {
            statement.setObject(1, orderId);
            statement.setObject(2, operationId);
            try (var result = statement.executeQuery()) {
                if (!result.next() || result.getLong(1) != amount) {
                    throw new SQLException("Payment operation does not match the existing supply order");
                }
            }
        }
    }

    private void createFulfillment(Connection connection, UUID orderId, UUID restaurantId) throws SQLException {
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_shipments (shipment_id, order_id, restaurant_id, state) "
                        + "VALUES (?, ?, ?, 'CREATED')")) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, orderId);
            statement.setObject(3, restaurantId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_packages (package_id, shipment_id, state) VALUES (?, ?, 'IN_TRANSIT')")) {
            statement.setObject(1, packageId);
            statement.setObject(2, shipmentId);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_package_lines (package_id, sku, quantity) "
                        + "SELECT ?, sku, quantity FROM supply_order_lines WHERE order_id = ?")) {
            statement.setObject(1, packageId);
            statement.setObject(2, orderId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalStateException("Submitted order has no order lines");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO supply_shipment_runtime
                    (shipment_id, revision, checkpoint_stage, journey_snapshot_version,
                     journey_snapshot, recovery_outcome)
                VALUES (?, 1, 'PENDING_MANUAL', 1, '{"schema":"route-not-pinned"}', 'PENDING_MANUAL')
                """)) {
            statement.setObject(1, shipmentId);
            statement.executeUpdate();
        }
    }
}