package vn.restauranttycoon.worldoperation;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

public final class WorldOperationWorker {
    private final WorldOperationRepository operations;
    private final WorldProjectionApplier applier;
    private final Executor databaseExecutor;
    private final String instanceId;
    private final Duration lease;

    public WorldOperationWorker(
            WorldOperationRepository operations,
            WorldProjectionApplier applier,
            Executor databaseExecutor,
            String instanceId,
            Duration lease
    ) {
        this.operations = Objects.requireNonNull(operations, "operations");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.databaseExecutor = Objects.requireNonNull(databaseExecutor, "databaseExecutor");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.lease = Objects.requireNonNull(lease, "lease");
    }

    public CompletableFuture<WorldOperationRunResult> runOnce() {
        return database(() -> operations.claimNext(instanceId, lease))
                .thenCompose(claim -> claim
                        .map(this::apply)
                        .orElseGet(() -> CompletableFuture.completedFuture(
                                WorldOperationRunResult.NO_WORK)));
    }

    private CompletableFuture<WorldOperationRunResult> apply(WorldOperationClaim claim) {
        return database(() -> {
            operations.assertCurrentFence(claim);
            return null;
        }).thenCompose(ignored -> invokeApplier(claim))
                .thenCompose(ignored -> database(() -> {
                    operations.markApplied(claim);
                    return WorldOperationRunResult.APPLIED;
                }))
                .exceptionallyCompose(error -> markRepairRequired(claim, unwrap(error)));
    }

    private CompletableFuture<Void> invokeApplier(WorldOperationClaim claim) {
        try {
            return Objects.requireNonNull(
                    applier.applyAndValidate(claim),
                    "WorldProjectionApplier returned null");
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletableFuture<WorldOperationRunResult> markRepairRequired(
            WorldOperationClaim claim,
            Throwable failure
    ) {
        return database(() -> {
            operations.markRepairRequired(claim, failureMessage(failure));
            return WorldOperationRunResult.REPAIR_REQUIRED;
        }).exceptionallyCompose(repairError -> {
            failure.addSuppressed(unwrap(repairError));
            return CompletableFuture.failedFuture(failure);
        });
    }

    private <T> CompletableFuture<T> database(SqlSupplier<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (SQLException exception) {
                throw new CompletionException(exception);
            }
        }, databaseExecutor);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String failureMessage(Throwable failure) {
        String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
