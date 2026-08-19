package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Chạy checkpoint CAS trên executor được caller cấp, thường là database executor. */
public final class SupplyRuntimeTransitionCoordinator implements AutoCloseable {
    private final Executor executor;
    private final SupplyRuntimeTransitionApplier applier;
    private final AtomicBoolean closed = new AtomicBoolean();

    public SupplyRuntimeTransitionCoordinator(
            Executor executor, SupplyRuntimeTransitionApplier applier) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.applier = Objects.requireNonNull(applier, "applier");
    }

    public CompletableFuture<SupplyRuntimeTransitionResult> transition(
            SupplyRuntimeClaim claim,
            SupplyRuntimeProjection projection,
            UUID operationId) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(operationId, "operationId");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("runtime coordinator is closed"));
        }
        SupplyRuntimeTransitionCommand command = SupplyRuntimeTransitionCommandPlanner.plan(
                projection, operationId);
        if (command == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("terminal checkpoint has no transition command"));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return applier.apply(claim, command);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            }, executor);
        } catch (RuntimeException submissionFailure) {
            return CompletableFuture.failedFuture(submissionFailure);
        }
    }

    public Result tryTransition(
            SupplyRuntimeClaim claim,
            SupplyRuntimeProjection projection,
            UUID operationId) {
        if (closed.get()) return Result.CLOSED;
        transition(claim, projection, operationId);
        return Result.QUEUED;
    }

    @Override
    public void close() {
        closed.set(true);
    }

    public enum Result {
        QUEUED,
        CLOSED
    }
}
