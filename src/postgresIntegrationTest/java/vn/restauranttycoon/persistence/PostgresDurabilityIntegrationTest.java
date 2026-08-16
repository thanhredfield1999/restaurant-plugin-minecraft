package vn.restauranttycoon.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

@EnabledIfEnvironmentVariable(named = "RT_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.+")
class PostgresDurabilityIntegrationTest extends PostgresIntegrationSupport {
    @Test
    void flywayRerunAndConcurrentPurchaseRetryStayIdempotent() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema())
                .defaultSchema(schema())
                .locations("classpath:db/migration")
                .load();
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("12", flyway.info().current().getVersion().getVersion());

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
