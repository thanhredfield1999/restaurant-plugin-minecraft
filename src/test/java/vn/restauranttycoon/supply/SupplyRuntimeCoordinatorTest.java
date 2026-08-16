package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplyRuntimeCoordinatorTest {
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
    void listsCreatedShipmentAsRuntimeWork() throws Exception {
        SupplyFulfillmentRecord record = repository.createForPaidOrder(order, restaurant);
        List<SupplyRuntimeWork> work = repository.findRuntimeWork(10);
        assertEquals(1, work.size());
        assertEquals(record.shipmentId(), work.get(0).shipmentId());
        assertEquals(restaurant, work.get(0).restaurantId());
    }

    @Test
    void coordinatorDoesNotRunTwoPollsAtOnce() throws Exception {
        SupplyFulfillmentRecord record = repository.createForPaidOrder(order, restaurant);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger calls = new AtomicInteger();
        SupplyRuntimeCoordinator coordinator = new SupplyRuntimeCoordinator(
                repository, executor, work -> {
                    calls.incrementAndGet();
                    try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                });
        coordinator.poll();
        coordinator.poll();
        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    @Test
    void closeBeforeQueuedPollRunsPreventsHandlerDispatch() throws Exception {
        repository.createForPaidOrder(order, restaurant);
        java.util.ArrayList<Runnable> queued = new java.util.ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        SupplyRuntimeCoordinator coordinator = new SupplyRuntimeCoordinator(
                repository, queued::add, work -> calls.incrementAndGet());

        assertTrue(coordinator.poll());
        coordinator.close();
        queued.get(0).run();

        assertEquals(0, calls.get());
    }

    @Test
    void pollAfterCloseIsRejectedWithoutQueuingWork() {
        java.util.ArrayList<Runnable> queued = new java.util.ArrayList<>();
        SupplyRuntimeCoordinator coordinator = new SupplyRuntimeCoordinator(
                repository, queued::add, work -> { });

        coordinator.close();
        coordinator.close();

        assertEquals(false, coordinator.poll());
        assertTrue(queued.isEmpty());
    }

    @Test
    void executorRejectionDoesNotLeaveCoordinatorInFlight() {
        AtomicInteger submissions = new AtomicInteger();
        SupplyRuntimeCoordinator coordinator = new SupplyRuntimeCoordinator(
                repository,
                task -> {
                    submissions.incrementAndGet();
                    throw new RejectedExecutionException("executor stopped");
                },
                work -> { });

        assertThrows(RejectedExecutionException.class, coordinator::poll);
        assertThrows(RejectedExecutionException.class, coordinator::poll);

        assertEquals(2, submissions.get());
    }

    @Test
    void closeFromHandlerStopsDispatchBeforeNextWork() throws Exception {
        repository.createForPaidOrder(order, restaurant);
        UUID secondOrder = UUID.randomUUID();
        try (Connection c = dataSource.getConnection()) {
            insert(c, "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')", secondOrder, UUID.randomUUID(), restaurant, UUID.randomUUID());
            insert(c, "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)", secondOrder);
            insert(c, "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)", secondOrder, UUID.randomUUID());
        }
        repository.createForPaidOrder(secondOrder, restaurant);
        AtomicInteger calls = new AtomicInteger();
        SupplyRuntimeCoordinator[] coordinator = new SupplyRuntimeCoordinator[1];
        coordinator[0] = new SupplyRuntimeCoordinator(repository, Runnable::run, work -> {
            calls.incrementAndGet();
            coordinator[0].close();
        });

        assertTrue(coordinator[0].poll());

        assertEquals(1, calls.get());
    }

    private static void insert(Connection c, String sql, Object... values) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
        }
    }
}
