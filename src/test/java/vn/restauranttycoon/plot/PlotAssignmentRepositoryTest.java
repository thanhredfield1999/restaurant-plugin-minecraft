package vn.restauranttycoon.plot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlotAssignmentRepositoryTest {
    private DataSource dataSource;
    private PlotAssignmentRepository assignments;

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
        assignments = new PlotAssignmentRepository(dataSource);
    }

    @Test
    void releaseAndReassignAlwaysAdvanceFenceAndRevision() throws SQLException {
        UUID firstOwner = UUID.randomUUID();
        PlotAssignment first = assignments.assign("plot_1", firstOwner, "server_1");
        PlotAssignment released = assignments.release(
                "plot_1", firstOwner, first.fenceToken());
        PlotAssignment second = assignments.assign(
                "plot_1", UUID.randomUUID(), "server_1");

        assertEquals(1, first.fenceToken());
        assertEquals(2, released.fenceToken());
        assertTrue(released.accountId().isEmpty());
        assertEquals(3, second.fenceToken());
        assertEquals(3, second.assignmentRevision());
    }

    @Test
    void staleReleaseCannotClearANewerAssignment() throws SQLException {
        UUID firstOwner = UUID.randomUUID();
        PlotAssignment first = assignments.assign("plot_1", firstOwner, "server_1");
        assignments.release("plot_1", firstOwner, first.fenceToken());
        UUID secondOwner = UUID.randomUUID();
        PlotAssignment second = assignments.assign("plot_1", secondOwner, "server_1");

        assertThrows(PlotAssignmentConflictException.class,
                () -> assignments.release("plot_1", firstOwner, first.fenceToken()));
        assertEquals(second, assignments.find("plot_1").orElseThrow());
    }

    @Test
    void accountAndPlotCanOnlyHaveOneActiveAssignment() throws SQLException {
        UUID owner = UUID.randomUUID();
        assignments.assign("plot_1", owner, "server_1");

        assertThrows(PlotAssignmentConflictException.class,
                () -> assignments.assign("plot_1", UUID.randomUUID(), "server_1"));
        assertThrows(PlotAssignmentConflictException.class,
                () -> assignments.assign("plot_2", owner, "server_1"));
    }

    @Test
    void identicalAssignmentRetryReturnsCurrentFenceWithoutAdvancingIt() throws SQLException {
        UUID owner = UUID.randomUUID();
        PlotAssignment first = assignments.assign("plot_1", owner, "server_1");

        PlotAssignment retry = assignments.assign("plot_1", owner, "server_1");

        assertEquals(first, retry);
        assertEquals(1, retry.fenceToken());
        assertEquals(1, retry.assignmentRevision());
    }

    @Test
    void allocateReturnsExistingPlotOrFirstAvailableConfiguredPlot() throws SQLException {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        PlotAssignment first = assignments.allocate(
                firstOwner, "server_1", List.of("plot_1", "plot_2"));
        PlotAssignment retry = assignments.allocate(
                firstOwner, "server_1", List.of("plot_1", "plot_2"));
        PlotAssignment second = assignments.allocate(
                secondOwner, "server_1", List.of("plot_1", "plot_2"));

        assertEquals("plot_1", first.plotId());
        assertEquals(first, retry);
        assertEquals("plot_2", second.plotId());
    }

    @Test
    void allocateFailsClosedWhenNoConfiguredPlotIsAvailable() throws SQLException {
        assignments.allocate(UUID.randomUUID(), "server_1", List.of("plot_1"));

        assertThrows(PlotAssignmentConflictException.class,
                () -> assignments.allocate(
                        UUID.randomUUID(), "server_1", List.of("plot_1")));
    }

    @Test
    void concurrentAllocationsNeverShareAPlot() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<PlotAssignment> first = CompletableFuture.supplyAsync(
                    () -> allocate(UUID.randomUUID()), executor);
            CompletableFuture<PlotAssignment> second = CompletableFuture.supplyAsync(
                    () -> allocate(UUID.randomUUID()), executor);

            PlotAssignment firstResult = first.get();
            PlotAssignment secondResult = second.get();

            assertTrue(!firstResult.plotId().equals(secondResult.plotId()));
        } finally {
            executor.shutdownNow();
        }
    }

    private PlotAssignment allocate(UUID accountId) {
        try {
            return new PlotAssignmentRepository(dataSource).allocate(
                    accountId, "server_1", List.of("plot_1", "plot_2"));
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
