package vn.restauranttycoon.plot;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import vn.restauranttycoon.persistence.DatabaseManager;

public final class PlotAssignmentService {
    private final DatabaseManager database;

    public PlotAssignmentService(DatabaseManager database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public CompletableFuture<PlotAssignment> assign(
            String plotId,
            UUID accountId,
            String serverId
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new PlotAssignmentRepository(database.requireDataSource())
                        .assign(plotId, accountId, serverId);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, database.executor());
    }

    public CompletableFuture<PlotAssignment> allocate(
            UUID accountId,
            String serverId,
            List<String> configuredPlotIds
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new PlotAssignmentRepository(database.requireDataSource())
                        .allocate(accountId, serverId, configuredPlotIds);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, database.executor());
    }

    public CompletableFuture<Optional<PlotAssignment>> find(String plotId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new PlotAssignmentRepository(database.requireDataSource()).find(plotId);
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, database.executor());
    }
}
