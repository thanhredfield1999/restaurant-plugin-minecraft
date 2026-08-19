package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplyFulfillmentRepositoryTest {
    private DataSource dataSource;
    private SupplyFulfillmentRepository repository;
    private final UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private final UUID restaurantId = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
        dataSource = database;
        repository = new SupplyFulfillmentRepository(dataSource);
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) "
                        + "VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')")) {
            statement.setObject(1, orderId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, restaurantId);
            statement.setObject(4, playerId);
            statement.executeUpdate();
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)")) {
            statement.setObject(1, orderId);
            statement.setObject(2, UUID.randomUUID());
            statement.executeUpdate();
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) "
                        + "VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)")) {
            statement.setObject(1, orderId);
            statement.executeUpdate();
        }
    }

    @Test
    void createsOneShipmentAndPackageForPaidOrderAndRetriesIdempotently() throws SQLException {
        SupplyFulfillmentRecord first = repository.createForPaidOrder(orderId, restaurantId);
        SupplyFulfillmentRecord retry = repository.createForPaidOrder(orderId, restaurantId);

        assertNotNull(first.shipmentId());
        assertNotNull(first.packageId());
        assertEquals(first, retry);
        assertEquals(SupplyShipmentState.CREATED, first.shipmentState());
        assertEquals(SupplyPackageState.IN_TRANSIT, first.packageState());
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT quantity FROM supply_package_lines WHERE package_id = ?")) {
            statement.setObject(1, first.packageId());
            try (var result = statement.executeQuery()) {
                assertEquals(true, result.next());
                assertEquals(4, result.getInt(1));
            }
        }
    }

    @Test
    void pinsRuntimeSnapshotFromSetupUsingSingleDatabaseRead() throws SQLException {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(orderId, restaurantId);
        String plotId = restaurantId.toString();
        insertSetup(plotId);

        insertPlotAssignment(plotId);
        repository.pinRuntimeSnapshotFromSetup(fulfillment.shipmentId(), restaurantId, playerId, plotId);

        assertEquals(true, repository.hasRecoverableRuntimeSnapshot(fulfillment.shipmentId()));
    }

    @Test
    void pinsRuntimeSnapshotOnlyForCreatedShipmentAndMatchingRestaurant() throws SQLException {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(orderId, restaurantId);
        repository.pinRuntimeSnapshot(fulfillment.shipmentId(), restaurantId, 1, "{\"version\":1}");

        assertEquals(true, repository.hasRecoverableRuntimeSnapshot(fulfillment.shipmentId()));
        assertThrows(IllegalStateException.class, () -> repository.pinRuntimeSnapshot(
                fulfillment.shipmentId(), UUID.randomUUID(), 1, "{\"version\":1}"));
    }

    @Test
    void claimFencesExpiredWorkerAndAllowsTakeover() throws SQLException {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(orderId, restaurantId);
        SupplyRuntimeClaim first = repository.claimNext("worker-a", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(fulfillment.shipmentId(), first.shipmentId());
        assertEquals(1, first.attemptCount());
        expireClaim(fulfillment.shipmentId());
        SupplyRuntimeClaim replacement = repository.claimNext("worker-b", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(fulfillment.shipmentId(), replacement.shipmentId());
        assertEquals(2, replacement.attemptCount());
        assertNotEquals(first.claimToken(), replacement.claimToken());
        assertThrows(StaleSupplyRuntimeClaimException.class, () -> repository.dispatch(first));
        repository.dispatch(replacement);
        assertEquals(SupplyShipmentState.IN_TRANSIT, shipmentState(fulfillment.shipmentId()));
    }

    @Test
    void repositoryTransitionExecutorPreservesClaimFenceAndCommand() throws SQLException {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(orderId, restaurantId);
        String plotId = restaurantId.toString();
        insertSetup(plotId);
        insertPlotAssignment(plotId);
        repository.pinRuntimeSnapshotFromSetup(fulfillment.shipmentId(), restaurantId, playerId, plotId);

        SupplyRuntimeClaim claim = repository.claimNext("worker-a", Duration.ofSeconds(30)).orElseThrow();
        repository.dispatch(claim);
        SupplyRuntimeTransitionCommand command = new SupplyRuntimeTransitionCommand(
                2, "DELIVERY_ENTRY", 0, "DELIVERY_STOP", 0,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        SupplyRuntimeTransitionResult result = new SupplyRuntimeRepositoryTransitionExecutor(repository)
                .execute(new SupplyRuntimeTransitionRequest(claim, command));

        assertEquals(SupplyRuntimeTransitionResult.APPLIED, result);
        assertEquals(SupplyRuntimeTransitionResult.IDEMPOTENT_REPLAY,
                new SupplyRuntimeRepositoryTransitionExecutor(repository)
                        .execute(new SupplyRuntimeTransitionRequest(claim, command)));
    }

    @Test
    void renewRejectsExpiredClaim() throws SQLException {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(orderId, restaurantId);
        SupplyRuntimeClaim claim = repository.claimNext("worker-a", Duration.ofSeconds(30)).orElseThrow();
        expireClaim(fulfillment.shipmentId());
        assertThrows(StaleSupplyRuntimeClaimException.class,
                () -> repository.renew(claim, Duration.ofSeconds(30)));
    }

    private void expireClaim(UUID shipmentId) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "UPDATE supply_shipments SET claim_expires_at = ? WHERE shipment_id = ?")) {
            statement.setObject(1, Instant.EPOCH);
            statement.setObject(2, shipmentId);
            statement.executeUpdate();
        }
    }

    private void insertPlotAssignment(String plotId) throws SQLException {
        try (var connection = dataSource.getConnection(); var account = connection.prepareStatement(
                "INSERT INTO economy_accounts (account_id, balance) VALUES (?, 1000)")) {
            account.setObject(1, playerId);
            account.executeUpdate();
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO plot_assignments (plot_id, account_id, server_id, fence_token, assignment_revision, assigned_at) VALUES (?, ?, 'test', 1, 1, CURRENT_TIMESTAMP)")) {
            statement.setString(1, plotId);
            statement.setObject(2, playerId);
            statement.executeUpdate();
        }
    }

    private void insertSetup(String plotId) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_setup_points (setup_scope, owner_id, point_type, world_name, x, y, z, yaw, pitch) VALUES ('RESTAURANT', ?, ?, 'world', ?, 64, 2, 90, 0)")) {
            String[] types = {"DELIVERY_ENTRY", "DELIVERY_STOP", "UNLOAD_POINT", "DELIVERY_EXIT", "DELIVERY_DESPAWN"};
            for (int i = 0; i < types.length; i++) {
                statement.setString(1, plotId);
                statement.setString(2, types[i]);
                statement.setDouble(3, i + 1);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private SupplyShipmentState shipmentState(UUID shipmentId) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT state FROM supply_shipments WHERE shipment_id = ?")) {
            statement.setObject(1, shipmentId);
            try (var result = statement.executeQuery()) {
                result.next();
                return SupplyShipmentState.valueOf(result.getString(1));
            }
        }
    }
}
