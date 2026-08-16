package vn.restauranttycoon.worldoperation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.EconomyRepository;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.purchase.PurchaseRepository;
import vn.restauranttycoon.purchase.PurchaseRequest;
import vn.restauranttycoon.plot.PlotAssignmentRepository;

class WorldOperationRepositoryTest {
    private DataSource dataSource;
    private WorldOperationRepository operations;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource = database;
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        operations = new WorldOperationRepository(dataSource);
        createPurchase();
    }

    @Test
    void claimCarriesProjectionFenceAndOnlyOneWorkerCanOwnIt() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Optional<WorldOperationClaim>> first = CompletableFuture.supplyAsync(
                    () -> claim("worker-a"), executor);
            CompletableFuture<Optional<WorldOperationClaim>> second = CompletableFuture.supplyAsync(
                    () -> claim("worker-b"), executor);

            Optional<WorldOperationClaim> firstResult = first.get();
            Optional<WorldOperationClaim> secondResult = second.get();
            assertNotEquals(firstResult.isPresent(), secondResult.isPresent());
            WorldOperationClaim winner = firstResult.orElseGet(secondResult::orElseThrow);
            assertEquals("plot_1", winner.plotId());
            assertEquals(7, winner.requiredFenceToken());
            assertEquals(2, winner.targetStageRevision());
            assertEquals(1, winner.attemptCount());
            assertTrue(operations.claimNext("worker-c", Duration.ofSeconds(30)).isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void onboardingRequestIsFreeAndIdempotentWithinAssignmentFence() throws SQLException {
        UUID owner = purchaseOwner();
        int purchaseCount = scalarInt("SELECT COUNT(*) FROM purchases");
        int ledgerCount = scalarInt("SELECT COUNT(*) FROM economy_ledger");

        UUID first = operations.requestInitialStage(owner, "plot_1", 7, 1);
        UUID duplicate = operations.requestInitialStage(owner, "plot_1", 7, 1);

        assertEquals(first, duplicate);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM world_operations "
                + "WHERE operation_source = 'ONBOARDING'"));
        assertEquals(purchaseCount, scalarInt("SELECT COUNT(*) FROM purchases"));
        assertEquals(ledgerCount, scalarInt("SELECT COUNT(*) FROM economy_ledger"));
    }

    @Test
    void onboardingRequestRejectsStaleOwnerOrFence() throws SQLException {
        UUID owner = purchaseOwner();

        assertThrows(StalePlotFenceException.class,
                () -> operations.requestInitialStage(UUID.randomUUID(), "plot_1", 7, 1));
        assertThrows(StalePlotFenceException.class,
                () -> operations.requestInitialStage(owner, "plot_1", 6, 1));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM world_operations "
                + "WHERE operation_source = 'ONBOARDING'"));
    }

    @Test
    void onboardingOperationUsesSameClaimFenceAndProjectionPipeline() throws SQLException {
        UUID owner = purchaseOwner();
        UUID operationId = operations.requestInitialStage(owner, "plot_1", 7, 1);
        cancelPurchaseWorldOperation();

        WorldOperationClaim onboardingClaim = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(operationId, onboardingClaim.worldOperationId());
        operations.assertCurrentFence(onboardingClaim);
        operations.markApplied(onboardingClaim);

        assertEquals(1, projectionLong("stage_revision"));
    }

    @Test
    void activeOwnerCanRenewAndComplete() throws SQLException {
        WorldOperationClaim claim = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();

        WorldOperationClaim renewed = operations.renew(claim, Duration.ofSeconds(60));
        operations.markApplied(renewed);

        assertEquals("APPLIED", scalarString("state"));
        assertEquals("COMPLETE", scalarString("phase"));
        assertEquals("plot_1", projectionString("plot_id"));
        assertEquals(7, projectionLong("fence_token"));
        assertEquals(2, projectionLong("stage_revision"));
        assertEquals(renewed.worldOperationId(), projectionUuid("last_world_operation_id"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM world_operations "
                + "WHERE claim_token IS NOT NULL OR claimed_by_instance IS NOT NULL "
                + "OR claim_expires_at IS NOT NULL"));
        assertTrue(operations.claimNext("worker-b", Duration.ofSeconds(30)).isEmpty());
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldCallbackIsFenced() throws SQLException {
        WorldOperationClaim stale = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();
        expireLease(stale.worldOperationId());

        WorldOperationClaim current = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();

        assertNotEquals(stale.claimToken(), current.claimToken());
        assertEquals(2, current.attemptCount());
        assertThrows(StaleWorldOperationClaimException.class,
                () -> operations.markApplied(stale));
        operations.markApplied(current);
        assertEquals("APPLIED", scalarString("state"));
    }

    @Test
    void expiredClaimCannotBeRenewed() throws SQLException {
        WorldOperationClaim claim = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();
        expireLease(claim.worldOperationId());

        assertThrows(StaleWorldOperationClaimException.class,
                () -> operations.renew(claim, Duration.ofSeconds(30)));
    }

    @Test
    void repairRequiredReleasesLeaseAndRetriesWithoutNewPurchase() throws SQLException {
        WorldOperationClaim first = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();
        operations.markRepairRequired(first, "projection validation failed");

        assertEquals("REPAIR_REQUIRED", scalarString("state"));
        assertEquals("projection validation failed", scalarString("last_error"));
        WorldOperationClaim retry = operations.claimNext(
                "worker-b", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(2, retry.attemptCount());
        assertEquals(first.worldOperationId(), retry.worldOperationId());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM purchases"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM world_operations"));
    }

    @Test
    void reassignmentFencesActiveWorldMutationAndCompletion() throws SQLException {
        WorldOperationClaim claim = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();
        UUID owner = purchaseOwner();
        PlotAssignmentRepository assignments = new PlotAssignmentRepository(dataSource);
        assignments.release("plot_1", owner, 7);
        assignments.assign("plot_1", UUID.randomUUID(), "server_1");

        assertThrows(StalePlotFenceException.class,
                () -> operations.assertCurrentFence(claim));
        assertThrows(StalePlotFenceException.class,
                () -> operations.markApplied(claim));
        assertEquals("APPLYING", scalarString("state"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM plot_projection_state"));
    }

    @Test
    void olderStageCannotMoveProjectionBackwardWithinTheSameFence() throws SQLException {
        WorldOperationClaim newer = operations.claimNext(
                "worker-a", Duration.ofSeconds(30)).orElseThrow();
        operations.markApplied(newer);
        insertCommittedOperation(newer, 1);
        WorldOperationClaim older = operations.claimNext(
                "worker-b", Duration.ofSeconds(30)).orElseThrow();

        assertThrows(StaleWorldOperationClaimException.class,
                () -> operations.markApplied(older));
        assertEquals(2, projectionLong("stage_revision"));
        assertEquals("APPLYING", operationState(older.worldOperationId()));
    }

    private Optional<WorldOperationClaim> claim(String instanceId) {
        try {
            return operations.claimNext(instanceId, Duration.ofSeconds(30));
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void createPurchase() throws SQLException {
        UUID accountId = UUID.randomUUID();
        new EconomyRepository(dataSource).apply(
                accountId, OperationKey.create(), 500, "TEST_GRANT");
        insertPlotAssignment(accountId);
        new PurchaseRepository(dataSource).commit(new PurchaseRequest(
                accountId,
                OperationKey.create(),
                "starter_table",
                1,
                new CurrencyAmount(125),
                "plot_1",
                7,
                2));
    }

    private void insertPlotAssignment(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO plot_assignments
                         (plot_id, account_id, server_id, fence_token,
                          assignment_revision, assigned_at)
                     VALUES ('plot_1', ?, 'server_1', 7, 7, CURRENT_TIMESTAMP)
                     """)) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    private UUID purchaseOwner() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT account_id FROM purchases")) {
            result.next();
            return result.getObject(1, UUID.class);
        }
    }

    private void expireLease(UUID operationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE world_operations
                     SET claim_expires_at = ?
                     WHERE world_operation_id = ?
                     """)) {
            statement.setObject(1, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
            statement.setObject(2, operationId);
            statement.executeUpdate();
        }
    }

    private void insertCommittedOperation(WorldOperationClaim existing, long stageRevision)
            throws SQLException {
        UUID purchaseId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        UUID ledgerOperationId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ledger = connection.prepareStatement("""
                    INSERT INTO economy_ledger
                        (operation_id, account_id, delta, balance_after, account_revision, reason)
                    SELECT ?, account_id, 1, 376, 3, 'TEST_GRANT' FROM purchases
                    FETCH FIRST 1 ROW ONLY
                    """)) {
                ledger.setObject(1, ledgerOperationId);
                ledger.executeUpdate();
            }
            try (PreparedStatement purchase = connection.prepareStatement("""
                    INSERT INTO purchases
                        (purchase_id, operation_id, account_id, unlock_id, definition_version,
                         price, state, plot_id, plot_fence_token, target_stage_revision)
                    SELECT ?, ?, account_id, 'older_stage', 1, 1, 'COMMITTED',
                           plot_id, plot_fence_token, ?
                    FROM purchases FETCH FIRST 1 ROW ONLY
                    """)) {
                purchase.setObject(1, purchaseId);
                purchase.setObject(2, ledgerOperationId);
                purchase.setLong(3, stageRevision);
                purchase.executeUpdate();
            }
            try (PreparedStatement operation = connection.prepareStatement("""
                    INSERT INTO world_operations
                        (world_operation_id, purchase_id, operation_type, plot_id,
                         required_fence_token, target_stage_revision, state, phase)
                    VALUES (?, ?, 'APPLY_STAGE', ?, ?, ?, 'REQUESTED', 'PENDING')
                    """)) {
                operation.setObject(1, operationId);
                operation.setObject(2, purchaseId);
                operation.setString(3, existing.plotId());
                operation.setLong(4, existing.requiredFenceToken());
                operation.setLong(5, stageRevision);
                operation.executeUpdate();
            }
            connection.commit();
        }
    }

    private void cancelPurchaseWorldOperation() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE world_operations
                     SET state = 'CANCELLED_BY_ADMIN'
                     WHERE operation_source = 'PURCHASE'
                     """)) {
            statement.executeUpdate();
        }
    }

    private String operationState(UUID operationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM world_operations WHERE world_operation_id = ?")) {
            statement.setObject(1, operationId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private String projectionString(String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT " + column + " FROM plot_projection_state")) {
            result.next();
            return result.getString(1);
        }
    }

    private long projectionLong(String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT " + column + " FROM plot_projection_state")) {
            result.next();
            return result.getLong(1);
        }
    }

    private UUID projectionUuid(String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT " + column + " FROM plot_projection_state")) {
            result.next();
            return result.getObject(1, UUID.class);
        }
    }

    private String scalarString(String column) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT " + column + " FROM world_operations")) {
            result.next();
            return result.getString(1);
        }
    }

    private int scalarInt(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getInt(1);
        }
    }
}
