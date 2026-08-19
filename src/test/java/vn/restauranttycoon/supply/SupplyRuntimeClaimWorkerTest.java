package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SupplyRuntimeClaimWorkerTest {
    private DataSource dataSource;
    private SupplyFulfillmentRepository repository;
    private UUID order;
    private UUID restaurant;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        dataSource = db;
        repository = new SupplyFulfillmentRepository(db);
        order = UUID.randomUUID();
        restaurant = UUID.randomUUID();
        try (Connection c = db.getConnection()) {
            insert(c, "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')", order, UUID.randomUUID(), restaurant, UUID.randomUUID());
            insert(c, "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)", order);
            insert(c, "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)", order, UUID.randomUUID());
        }
    }

    @Test
    void claimsCreatedShipmentAndDispatchesWithFence() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        insertRuntimeSnapshot(fulfillment.shipmentId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SupplyRuntimeClaimWorker worker = new SupplyRuntimeClaimWorker(
                    repository, executor, "runtime-a", java.time.Duration.ofSeconds(30));
            assertEquals(SupplyRuntimeClaimWorker.SupplyRuntimeClaimResult.DISPATCHED,
                    worker.runOnce().get(2, TimeUnit.SECONDS));
            assertEquals(SupplyShipmentState.IN_TRANSIT, repository.findRuntimeWork(10).get(0).shipmentState());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void missingRuntimeSnapshotMovesShipmentToManualRecovery() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SupplyRuntimeClaimWorker worker = new SupplyRuntimeClaimWorker(
                    repository, executor, "runtime-a", java.time.Duration.ofSeconds(30));
            assertEquals(SupplyRuntimeClaimWorker.SupplyRuntimeClaimResult.PENDING_MANUAL,
                    worker.runOnce().get(2, TimeUnit.SECONDS));
            assertEquals(SupplyShipmentState.CREATED, repository.findRuntimeWork(10).get(0).shipmentState());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void closePreventsNewClaim() throws Exception {
        SupplyRuntimeClaimWorker worker = new SupplyRuntimeClaimWorker(
                repository, Runnable::run, "runtime-a", java.time.Duration.ofSeconds(30));
        worker.close();
        assertEquals(SupplyRuntimeClaimWorker.SupplyRuntimeClaimResult.CLOSED, worker.runOnce().join());
    }

    @Test
    void claimedArrivedShipmentDoesNotMutateState() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        repository.dispatch(fulfillment.shipmentId());
        repository.arrive(fulfillment.shipmentId());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SupplyRuntimeClaimWorker worker = new SupplyRuntimeClaimWorker(
                    repository, executor, "runtime-a", java.time.Duration.ofSeconds(30));
            assertEquals(SupplyRuntimeClaimWorker.SupplyRuntimeClaimResult.CLAIMED_NO_MUTATION,
                    worker.runOnce().get(2, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void insert(Connection c, String sql, Object... values) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
        }
    }

    private void insertRuntimeSnapshot(UUID shipmentId) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            insert(c, "UPDATE supply_shipment_runtime SET checkpoint_stage = 'DELIVERY_ENTRY', journey_snapshot = '{\"schema\":\"test\"}', recovery_outcome = 'NONE', unload_deadline_at = NULL WHERE shipment_id = ?", shipmentId);
        }
    }
}
