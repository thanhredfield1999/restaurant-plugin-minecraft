package vn.restauranttycoon.supply;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

public final class SupplyRuntimeFixtureRepository {
    private static final String SNAPSHOT = "{\"version\":1,\"owner\":\"fixture\",\"steps\":["
            + "{\"stage\":\"DELIVERY_ENTRY\",\"world\":\"rt-flat-test\",\"x\":0.0,\"y\":65.0,\"z\":0.0,\"yaw\":0.0,\"pitch\":0.0},"
            + "{\"stage\":\"DELIVERY_STOP\",\"world\":\"rt-flat-test\",\"x\":1.0,\"y\":65.0,\"z\":0.0,\"yaw\":0.0,\"pitch\":0.0},"
            + "{\"stage\":\"UNLOAD_POINT\",\"world\":\"rt-flat-test\",\"x\":2.0,\"y\":65.0,\"z\":0.0,\"yaw\":0.0,\"pitch\":0.0},"
            + "{\"stage\":\"DELIVERY_EXIT\",\"world\":\"rt-flat-test\",\"x\":3.0,\"y\":65.0,\"z\":0.0,\"yaw\":0.0,\"pitch\":0.0},"
            + "{\"stage\":\"DELIVERY_DESPAWN\",\"world\":\"rt-flat-test\",\"x\":4.0,\"y\":65.0,\"z\":0.0,\"yaw\":0.0,\"pitch\":0.0}]}";
    private final DataSource dataSource;

    public SupplyRuntimeFixtureRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public SupplyRuntimeFixture seed(UUID fixtureId, UUID restaurantId, UUID playerId) throws SQLException {
        Objects.requireNonNull(fixtureId, "fixtureId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(playerId, "playerId");
        UUID shipmentId = UUID.nameUUIDFromBytes((fixtureId + ":shipment").getBytes(StandardCharsets.UTF_8));
        UUID packageId = UUID.nameUUIDFromBytes((fixtureId + ":package").getBytes(StandardCharsets.UTF_8));
        UUID captureOperationId = UUID.nameUUIDFromBytes((fixtureId + ":capture").getBytes(StandardCharsets.UTF_8));
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                SupplyRuntimeFixture existing = find(c, fixtureId);
                if (existing != null) {
                    if (!existing.restaurantId().equals(restaurantId) || !existing.playerId().equals(playerId)) {
                        throw new IllegalStateException("fixture owner conflict");
                    }
                    c.commit();
                    return existing;
                }
                java.sql.Savepoint orderSavepoint = c.setSavepoint();
                try {
                    insert(c, "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')", fixtureId, fixtureId, restaurantId, playerId);
                } catch (SQLException duplicate) {
                    c.rollback(orderSavepoint);
                    SupplyRuntimeFixture concurrent = find(c, fixtureId);
                    if (concurrent == null || !concurrent.restaurantId().equals(restaurantId) || !concurrent.playerId().equals(playerId)) {
                        throw duplicate;
                    }
                    c.commit();
                    return concurrent;
                }
                insert(c, "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)", fixtureId);

                insert(c, "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)", fixtureId, captureOperationId);
                insert(c, "INSERT INTO supply_shipments (shipment_id, order_id, restaurant_id, state) VALUES (?, ?, ?, 'CREATED')", shipmentId, fixtureId, restaurantId);
                insert(c, "INSERT INTO supply_packages (package_id, shipment_id, state) VALUES (?, ?, 'IN_TRANSIT')", packageId, shipmentId);
                insert(c, "INSERT INTO supply_package_lines (package_id, sku, quantity) VALUES (?, 'tomato', 4)", packageId);
                insert(c, "INSERT INTO supply_shipment_runtime (shipment_id, revision, checkpoint_stage, checkpoint_index, journey_snapshot_version, journey_snapshot, recovery_outcome, unload_deadline_at) VALUES (?, 1, 'DELIVERY_ENTRY', 0, 1, ?, 'NONE', NULL)", shipmentId, SNAPSHOT);
                c.commit();
                return new SupplyRuntimeFixture(fixtureId, shipmentId, packageId, restaurantId, playerId);
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public int cleanupAllFixtures() throws SQLException {
        java.util.List<UUID> fixtureIds = new java.util.ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT order_id FROM supply_orders WHERE operation_id = order_id AND EXISTS "
                        + "(SELECT 1 FROM supply_shipment_runtime runtime JOIN supply_shipments shipment "
                        + "ON shipment.shipment_id = runtime.shipment_id WHERE shipment.order_id = supply_orders.order_id "
                        + "AND runtime.journey_snapshot LIKE ?)") ) {
            s.setString(1, "%\"owner\":\"fixture\"%");
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) fixtureIds.add(r.getObject(1, UUID.class));
            }
        }
        for (UUID fixtureId : fixtureIds) cleanup(fixtureId);
        return fixtureIds.size();
    }

    public boolean cleanup(UUID fixtureId) throws SQLException {
               try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int deleted;
                try (PreparedStatement s = c.prepareStatement(
                        "DELETE FROM warehouse_stock_operations WHERE package_id IN "
                                + "(SELECT package_id FROM supply_packages WHERE shipment_id IN "
                                + "(SELECT shipment_id FROM supply_shipments WHERE order_id = ?))")) {
                    s.setObject(1, fixtureId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM supply_package_lines WHERE package_id IN (SELECT package_id FROM supply_packages WHERE shipment_id IN (SELECT shipment_id FROM supply_shipments WHERE order_id = ?))")) {
                    s.setObject(1, fixtureId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM supply_packages WHERE shipment_id IN (SELECT shipment_id FROM supply_shipments WHERE order_id = ?)")) {
                    s.setObject(1, fixtureId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM supply_shipments WHERE order_id = ?")) {
                    s.setObject(1, fixtureId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM supply_order_lines WHERE order_id = ?")) {
                    s.setObject(1, fixtureId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM supply_payments WHERE order_id = ?")) {
                    s.setObject(1, fixtureId);
                    s.executeUpdate();
                }
                try (PreparedStatement s = c.prepareStatement("DELETE FROM supply_orders WHERE order_id = ?")) {
                    s.setObject(1, fixtureId);
                    deleted = s.executeUpdate();
                }
                c.commit();
                return deleted == 1;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean hasRecoverableProjection(UUID shipmentId) throws SQLException {
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT checkpoint_stage, journey_snapshot FROM supply_shipment_runtime WHERE shipment_id = ?")) {
            s.setObject(1, shipmentId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() && "DELIVERY_ENTRY".equals(r.getString(1)) && r.getString(2) != null;
            }
        }
    }

    private SupplyRuntimeFixture find(Connection c, UUID fixtureId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT shipment.shipment_id, package_row.package_id, order_row.restaurant_id, order_row.player_id FROM supply_orders order_row JOIN supply_shipments shipment ON shipment.order_id = order_row.order_id JOIN supply_packages package_row ON package_row.shipment_id = shipment.shipment_id WHERE order_row.operation_id = ?")) {
            s.setObject(1, fixtureId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new SupplyRuntimeFixture(fixtureId, r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getObject(3, UUID.class), r.getObject(4, UUID.class)) : null;
            }
        }
    }

    private static void insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
        }
    }
}
