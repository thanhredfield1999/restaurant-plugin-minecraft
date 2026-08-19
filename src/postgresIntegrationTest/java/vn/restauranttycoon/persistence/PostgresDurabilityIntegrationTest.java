package vn.restauranttycoon.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.util.Map;
import vn.restauranttycoon.market.MarketCycle;
import vn.restauranttycoon.market.MarketRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.EconomyRepository;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.plot.PlotAssignmentRepository;
import vn.restauranttycoon.purchase.PurchaseRepository;
import vn.restauranttycoon.purchase.PurchaseRequest;
import vn.restauranttycoon.worldoperation.StaleWorldOperationClaimException;
import vn.restauranttycoon.worldoperation.WorldOperationClaim;
import vn.restauranttycoon.worldoperation.WorldOperationRepository;
import vn.restauranttycoon.supply.SupplyFulfillmentRecord;
import vn.restauranttycoon.supply.SupplyFulfillmentRepository;
import vn.restauranttycoon.supply.SupplyRuntimeClaim;
import vn.restauranttycoon.supply.StaleSupplyRuntimeClaimException;

@EnabledIfEnvironmentVariable(named = "RT_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.+")
class PostgresDurabilityIntegrationTest extends PostgresIntegrationSupport {
    @Test
    void concurrentMarketWorkersConvergeOnOneCycleOnPostgres() throws Exception {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            CompletableFuture<?>[] attempts = new CompletableFuture<?>[48];
            for (int index = 0; index < attempts.length; index++) {
                attempts[index] = CompletableFuture.supplyAsync(() -> {
                    try {
                        return new MarketRepository(dataSource).ensureOpenCycle(
                                Map.of("tomato", 25L), now, Duration.ofMinutes(10));
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                }, executor);
            }
            CompletableFuture.allOf(attempts).get(30, TimeUnit.SECONDS);
            java.util.Set<UUID> cycleIds = new java.util.HashSet<>();
            for (CompletableFuture<?> attempt : attempts) {
                cycleIds.add(((MarketCycle) attempt.get()).cycleId());
            }
            assertEquals(1, cycleIds.size());
            assertEquals(1, count("market_cycles"));
            assertEquals(1, count("market_prices"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void flywayRerunAndConcurrentPurchaseRetryStayIdempotent() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema())
                .defaultSchema(schema())
                .locations("classpath:db/migration")
                .load();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("17", flyway.info().current().getVersion().getVersion());

        UUID owner = fundedAssignedOwner();
        PurchaseRequest request = request(owner, OperationKey.create());
        PurchaseRepository purchases = new PurchaseRepository(dataSource);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            CompletableFuture<?>[] attempts = new CompletableFuture<?>[250];
            for (int index = 0; index < attempts.length; index++) {
                attempts[index] = CompletableFuture.supplyAsync(() -> {
                    try {
                        return purchases.commit(request);
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                }, executor);
            }
            CompletableFuture.allOf(attempts).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, count("purchases"));
        assertEquals(1, count("unlocks"));
        assertEquals(1, count("world_operations"));
        assertEquals(2, count("economy_ledger"));
        assertEquals(new CurrencyAmount(375), new EconomyRepository(dataSource).balance(owner));
    }

    @Test
    void expiredClaimIsReclaimedAfterWorkerRestartAndOldCallbackIsFenced() throws Exception {
        UUID owner = fundedAssignedOwner();
        new PurchaseRepository(dataSource).commit(request(owner, OperationKey.create()));
        WorldOperationRepository firstProcess = new WorldOperationRepository(dataSource);
        WorldOperationClaim stale = firstProcess.claimNext(
                "paper-before-crash", Duration.ofSeconds(30)).orElseThrow();
        expire(stale.worldOperationId());

        WorldOperationRepository restartedProcess = new WorldOperationRepository(dataSource);
        WorldOperationClaim current = restartedProcess.claimNext(
                "paper-after-restart", Duration.ofSeconds(30)).orElseThrow();

        assertNotEquals(stale.claimToken(), current.claimToken());
        assertEquals(2, current.attemptCount());
        org.junit.jupiter.api.Assertions.assertThrows(
                StaleWorldOperationClaimException.class,
                () -> firstProcess.markApplied(stale));
        restartedProcess.markApplied(current);
        assertEquals("APPLIED", scalarString("SELECT state FROM world_operations"));
        assertEquals(1, count("plot_projection_state"));
        assertTrue(restartedProcess.claimNext(
                "paper-third", Duration.ofSeconds(30)).isEmpty());
    }

    @Test
    void concurrentSupplyWorkersClaimOneShipmentAndExpiredClaimIsFenced() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        seedPaidSupplyOrder(orderId, restaurantId, playerId);
        SupplyFulfillmentRecord fulfillment = new SupplyFulfillmentRepository(dataSource)
                .createForPaidOrder(orderId, restaurantId);
        SupplyFulfillmentRepository repository = new SupplyFulfillmentRepository(dataSource);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Optional<SupplyRuntimeClaim>> first = CompletableFuture.supplyAsync(
                    () -> claim(repository, "supply-a"), executor);
            CompletableFuture<Optional<SupplyRuntimeClaim>> second = CompletableFuture.supplyAsync(
                    () -> claim(repository, "supply-b"), executor);
            CompletableFuture.allOf(first, second).get(30, TimeUnit.SECONDS);
            Optional<SupplyRuntimeClaim> firstClaim = first.get();
            Optional<SupplyRuntimeClaim> secondClaim = second.get();
            long claimed = java.util.stream.Stream.of(firstClaim, secondClaim)
                    .filter(Optional::isPresent).count();
            assertEquals(1, claimed);
            SupplyRuntimeClaim stale = firstClaim.orElseGet(secondClaim::orElseThrow);
            expireSupplyClaim(fulfillment.shipmentId());
            SupplyRuntimeClaim replacement = repository.claimNext(
                    "supply-restarted", Duration.ofSeconds(30)).orElseThrow();
            assertNotEquals(stale.claimToken(), replacement.claimToken());
            assertThrows(StaleSupplyRuntimeClaimException.class, () -> repository.dispatch(stale));
            repository.dispatch(replacement);
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<SupplyRuntimeClaim> claim(SupplyFulfillmentRepository repository, String worker) {
        try {
            return repository.claimNext(worker, Duration.ofSeconds(30));
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void seedPaidSupplyOrder(UUID orderId, UUID restaurantId, UUID playerId) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement order = connection.prepareStatement(
                    "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')")) {
                order.setObject(1, orderId); order.setObject(2, UUID.randomUUID());
                order.setObject(3, restaurantId); order.setObject(4, playerId); order.executeUpdate();
            }
            try (PreparedStatement payment = connection.prepareStatement(
                    "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)")) {
                payment.setObject(1, orderId); payment.setObject(2, UUID.randomUUID()); payment.executeUpdate();
            }
            try (PreparedStatement line = connection.prepareStatement(
                    "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)")) {
                line.setObject(1, orderId); line.executeUpdate();
            }
            connection.commit();
        }
    }

    private void expireSupplyClaim(UUID shipmentId) throws SQLException {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "UPDATE supply_shipments SET claim_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE shipment_id = ?")) {
            statement.setObject(1, shipmentId); statement.executeUpdate();
        }
    }

    private UUID fundedAssignedOwner() throws SQLException {
        UUID owner = UUID.randomUUID();
        new EconomyRepository(dataSource).apply(
                owner, OperationKey.create(), 500, "TEST_GRANT");
        new PlotAssignmentRepository(dataSource).assign("plot_1", owner, "paper-1");
        return owner;
    }

    private PurchaseRequest request(UUID owner, OperationKey operationKey) {
        return new PurchaseRequest(
                owner,
                operationKey,
                "starter_table",
                1,
                new CurrencyAmount(125),
                "plot_1",
                1,
                1);
    }

    private void expire(UUID operationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE world_operations
                     SET claim_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute'
                     WHERE world_operation_id = ?
                     """)) {
            statement.setObject(1, operationId);
            statement.executeUpdate();
        }
    }

    private int count(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String scalarString(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getString(1);
        }
    }
}
