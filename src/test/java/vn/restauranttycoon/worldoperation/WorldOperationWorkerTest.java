package vn.restauranttycoon.worldoperation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.EconomyRepository;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.plot.PlotAssignmentRepository;
import vn.restauranttycoon.purchase.PurchaseRepository;
import vn.restauranttycoon.purchase.PurchaseRequest;

class WorldOperationWorkerTest {
    private DataSource dataSource;
    private WorldOperationRepository operations;
    private ExecutorService databaseExecutor;

    @BeforeEach
    void setUp() {
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
        databaseExecutor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        databaseExecutor.shutdownNow();
    }

    @Test
    void reportsNoWorkWithoutInvokingProjection() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        WorldOperationWorker worker = worker(claim -> {
            invoked.set(true);
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(WorldOperationRunResult.NO_WORK,
                worker.runOnce().get(5, TimeUnit.SECONDS));
        assertFalse(invoked.get());
    }

    @Test
    void appliesValidProjectionBeforeCompletingDurableOperation() throws Exception {
        createPurchase();
        WorldOperationWorker worker = worker(claim -> {
            assertEquals("APPLYING", operationState(claim.worldOperationId()));
            assertEquals(0, uncheckedCount("plot_projection_state"));
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(WorldOperationRunResult.APPLIED,
                worker.runOnce().get(5, TimeUnit.SECONDS));
        assertEquals("APPLIED", scalarString("SELECT state FROM world_operations"));
        assertEquals(2, scalarLong("SELECT stage_revision FROM plot_projection_state"));
    }

    @Test
    void failedProjectionIsReleasedForRepairWithoutCheckpoint() throws Exception {
        createPurchase();
        WorldOperationWorker worker = worker(claim ->
                CompletableFuture.failedFuture(new IllegalStateException("block mismatch")));

        assertEquals(WorldOperationRunResult.REPAIR_REQUIRED,
                worker.runOnce().get(5, TimeUnit.SECONDS));
        assertEquals("REPAIR_REQUIRED", scalarString("SELECT state FROM world_operations"));
        assertEquals("IllegalStateException: block mismatch",
                scalarString("SELECT last_error FROM world_operations"));
        assertEquals(0, count("plot_projection_state"));
    }

    @Test
    void synchronousProjectionFailureIsAlsoReleasedForRepair() throws Exception {
        createPurchase();
        WorldOperationWorker worker = worker(claim -> {
            throw new IllegalArgumentException("invalid stage definition");
        });

        assertEquals(WorldOperationRunResult.REPAIR_REQUIRED,
                worker.runOnce().get(5, TimeUnit.SECONDS));
        assertEquals("IllegalArgumentException: invalid stage definition",
                scalarString("SELECT last_error FROM world_operations"));
        assertEquals(0, count("plot_projection_state"));
    }

    @Test
    void staleFenceNeverInvokesProjectionAndMovesOperationToRepair() throws Exception {
        UUID owner = createPurchase();
        PlotAssignmentRepository assignments = new PlotAssignmentRepository(dataSource);
        assignments.release("plot_1", owner, 7);
        assignments.assign("plot_1", UUID.randomUUID(), "server_1");
        AtomicBoolean invoked = new AtomicBoolean();
        WorldOperationWorker worker = worker(claim -> {
            invoked.set(true);
            return CompletableFuture.completedFuture(null);
        });

        assertEquals(WorldOperationRunResult.REPAIR_REQUIRED,
                worker.runOnce().get(5, TimeUnit.SECONDS));
        assertFalse(invoked.get());
        assertEquals("REPAIR_REQUIRED", scalarString("SELECT state FROM world_operations"));
        assertEquals(0, count("plot_projection_state"));
    }

    private WorldOperationWorker worker(WorldProjectionApplier applier) {
        return new WorldOperationWorker(
                operations, applier, databaseExecutor, "test-instance", Duration.ofSeconds(30));
    }

    private UUID createPurchase() throws SQLException {
        UUID accountId = UUID.randomUUID();
        new EconomyRepository(dataSource).apply(
                accountId, OperationKey.create(), 500, "TEST_GRANT");
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
        new PurchaseRepository(dataSource).commit(new PurchaseRequest(
                accountId,
                OperationKey.create(),
                "starter_table",
                1,
                new CurrencyAmount(125),
                "plot_1",
                7,
                2));
        return accountId;
    }

    private String operationState(UUID operationId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT state FROM world_operations WHERE world_operation_id = ?")) {
            statement.setObject(1, operationId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int count(String table) throws SQLException {
        return (int) scalarLong("SELECT COUNT(*) FROM " + table);
    }

    private int uncheckedCount(String table) {
        try {
            return count(table);
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
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

    private long scalarLong(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getLong(1);
        }
    }
}
