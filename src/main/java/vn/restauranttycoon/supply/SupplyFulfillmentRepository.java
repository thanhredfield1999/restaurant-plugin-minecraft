package vn.restauranttycoon.supply;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

public final class SupplyFulfillmentRepository {
    private final DataSource dataSource;

    public SupplyFulfillmentRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public SupplyFulfillmentRecord createForPaidOrder(UUID orderId, UUID restaurantId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockPaidOrder(connection, orderId, restaurantId);
                SupplyFulfillmentRecord existing = find(connection, orderId);
                if (existing != null) {
                    connection.commit();
                    return existing;
                }

                UUID shipmentId = UUID.randomUUID();
                UUID packageId = UUID.randomUUID();
                insertShipment(connection, shipmentId, orderId, restaurantId);
                insertPackage(connection, packageId, shipmentId);
                copyOrderLines(connection, orderId, packageId);
                connection.commit();
                return new SupplyFulfillmentRecord(
                        shipmentId, packageId, SupplyShipmentState.CREATED, SupplyPackageState.IN_TRANSIT);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void dispatch(UUID shipmentId) throws SQLException {
        updateShipmentState(shipmentId, "CREATED", "IN_TRANSIT");
    }

    public void arrive(UUID shipmentId) throws SQLException {
        updateShipmentState(shipmentId, "IN_TRANSIT", "ARRIVED");
    }

    private void updateShipmentState(UUID shipmentId, String expected, String next) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE supply_shipments SET state = ? WHERE shipment_id = ? AND state = ?")) {
            statement.setString(1, next);
            statement.setObject(2, shipmentId);
            statement.setString(3, expected);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Invalid shipment transition");
            }
        }
    }

    public void handoff(UUID shipmentId, UUID playerId, UUID operationId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement s = c.prepareStatement("UPDATE supply_shipments SET state = 'HANDED_OFF', handoff_operation_id = ? WHERE shipment_id = ? AND state = 'ARRIVED'")) {
                    s.setObject(1, operationId); s.setObject(2, shipmentId);
                    if (s.executeUpdate() == 0 && !handoffAlreadyApplied(c, shipmentId, operationId)) throw new IllegalStateException("Invalid handoff");
                }
                try (PreparedStatement s = c.prepareStatement("UPDATE supply_packages SET state = 'HANDED_OFF', received_by = ?, received_at = CURRENT_TIMESTAMP WHERE shipment_id = ? AND state = 'IN_TRANSIT'")) { s.setObject(1, playerId); s.setObject(2, shipmentId); s.executeUpdate(); }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        }
    }

    public void handoff(UUID shipmentId, UUID operationId) throws SQLException {
        handoff(shipmentId, operationId, operationId);
    }

    public void stock(UUID packageId, UUID restaurantId, UUID operationId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (stockOperationExists(c, packageId)) { c.commit(); return; }
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT p.package_id FROM supply_packages p "
                                + "JOIN supply_shipments shipment ON shipment.shipment_id = p.shipment_id "
                                + "WHERE p.package_id = ? AND shipment.restaurant_id = ? "
                                + "AND p.state = 'HANDED_OFF' FOR UPDATE OF p")) {
                    s.setObject(1, packageId);
                    s.setObject(2, restaurantId);
                    try (ResultSet r = s.executeQuery()) {
                        if (!r.next()) throw new IllegalStateException("Package is not ready for stock at this restaurant");
                    }
                }
                try (PreparedStatement s = c.prepareStatement("INSERT INTO warehouse_stock_operations (operation_id, package_id, restaurant_id) VALUES (?, ?, ?)")) { s.setObject(1, operationId); s.setObject(2, packageId); s.setObject(3, restaurantId); s.executeUpdate(); }
                try (PreparedStatement s = c.prepareStatement("SELECT sku, quantity FROM supply_package_lines WHERE package_id = ?")) {
                    s.setObject(1, packageId); try (ResultSet r = s.executeQuery()) { while (r.next()) addStock(c, restaurantId, r.getString(1), r.getInt(2)); }
                }
                try (PreparedStatement s = c.prepareStatement("UPDATE supply_packages SET state = 'STOCKED', stocked_operation_id = ? WHERE package_id = ?")) { s.setObject(1, operationId); s.setObject(2, packageId); s.executeUpdate(); }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; } finally { c.setAutoCommit(true); }
        }
    }

    public java.util.List<SupplyRuntimeWork> findRuntimeWork(int limit) throws SQLException {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        java.util.List<SupplyRuntimeWork> work = new java.util.ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT s.shipment_id, p.package_id, s.restaurant_id, s.state, p.state "
                        + "FROM supply_shipments s JOIN supply_packages p ON p.shipment_id = s.shipment_id "
                        + "WHERE s.state NOT IN ('HANDED_OFF', 'CANCELLED') AND p.state <> 'STOCKED' "
                        + "ORDER BY s.shipment_id LIMIT ?")) {
            s.setInt(1, limit);
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    work.add(new SupplyRuntimeWork(
                            r.getObject(1, UUID.class),
                            r.getObject(2, UUID.class),
                            r.getObject(3, UUID.class),
                            SupplyShipmentState.valueOf(r.getString(4)),
                            SupplyPackageState.valueOf(r.getString(5))));
                }
            }
        }
        return java.util.List.copyOf(work);
    }

    public int quantity(UUID restaurantId, String sku) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT quantity FROM warehouse_stock WHERE restaurant_id = ? AND sku = ?")) {
            s.setObject(1, restaurantId); s.setString(2, sku); try (ResultSet r = s.executeQuery()) { return r.next() ? r.getInt(1) : 0; }
        }
    }

    private boolean handoffAlreadyApplied(Connection c, UUID shipmentId, UUID operationId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT 1 FROM supply_shipments WHERE shipment_id = ? AND handoff_operation_id = ?")) { s.setObject(1, shipmentId); s.setObject(2, operationId); try (ResultSet r = s.executeQuery()) { return r.next(); } }
    }

    private boolean stockOperationExists(Connection c, UUID packageId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT 1 FROM warehouse_stock_operations WHERE package_id = ?")) { s.setObject(1, packageId); try (ResultSet r = s.executeQuery()) { return r.next(); } }
    }

    private void addStock(Connection c, UUID restaurantId, String sku, int quantity) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "MERGE INTO warehouse_stock AS target "
                        + "USING (VALUES (?, ?, ?)) AS source (restaurant_id, sku, quantity) "
                        + "ON target.restaurant_id = source.restaurant_id AND target.sku = source.sku "
                        + "WHEN MATCHED THEN UPDATE SET quantity = target.quantity + source.quantity "
                        + "WHEN NOT MATCHED THEN INSERT (restaurant_id, sku, quantity) "
                        + "VALUES (source.restaurant_id, source.sku, source.quantity)")) {
            s.setObject(1, restaurantId);
            s.setString(2, sku);
            s.setInt(3, quantity);
            s.executeUpdate();
        }
    }

    private void lockPaidOrder(Connection connection, UUID orderId, UUID restaurantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT o.order_id FROM supply_orders o "
                        + "JOIN supply_payments p ON p.order_id = o.order_id "
                        + "WHERE o.order_id = ? AND o.restaurant_id = ? "
                        + "AND o.state = 'SUBMITTED' AND p.state = 'CAPTURED' FOR UPDATE")) {
            statement.setObject(1, orderId);
            statement.setObject(2, restaurantId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Order is not paid and submit-ready");
                }
            }
        }
    }

    private SupplyFulfillmentRecord find(Connection connection, UUID orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT s.shipment_id, s.state, p.package_id, p.state "
                        + "FROM supply_shipments s JOIN supply_packages p ON p.shipment_id = s.shipment_id "
                        + "WHERE s.order_id = ?")) {
            statement.setObject(1, orderId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new SupplyFulfillmentRecord(
                        result.getObject(1, UUID.class),
                        result.getObject(3, UUID.class),
                        SupplyShipmentState.valueOf(result.getString(2)),
                        SupplyPackageState.valueOf(result.getString(4)));
            }
        }
    }

    private void insertShipment(Connection connection, UUID shipmentId, UUID orderId, UUID restaurantId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_shipments (shipment_id, order_id, restaurant_id, state) "
                        + "VALUES (?, ?, ?, 'CREATED')")) {
            statement.setObject(1, shipmentId);
            statement.setObject(2, orderId);
            statement.setObject(3, restaurantId);
            statement.executeUpdate();
        }
    }

    private void insertPackage(Connection connection, UUID packageId, UUID shipmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_packages (package_id, shipment_id, state) VALUES (?, ?, 'IN_TRANSIT')")) {
            statement.setObject(1, packageId);
            statement.setObject(2, shipmentId);
            statement.executeUpdate();
        }
    }

    private void copyOrderLines(Connection connection, UUID orderId, UUID packageId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO supply_package_lines (package_id, sku, quantity) "
                        + "SELECT ?, sku, quantity FROM supply_order_lines WHERE order_id = ?")) {
            statement.setObject(1, packageId);
            statement.setObject(2, orderId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalStateException("Paid order has no order lines");
            }
        }
    }
}
