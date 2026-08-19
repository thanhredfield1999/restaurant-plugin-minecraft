package vn.restauranttycoon.supply;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Durable claim worker. Bukkit/Citizens world mutation remains outside this slice. */
public final class SupplyRuntimeClaimWorker implements AutoCloseable {
    private final SupplyFulfillmentRepository repository;
    private final Executor executor;
    private final String instanceId;
    private final Duration lease;
    private final SupplyRuntimeClaimDispatchHandler dispatchHandler;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean inFlight = new AtomicBoolean();

    public SupplyRuntimeClaimWorker(
            SupplyFulfillmentRepository repository,
            Executor executor,
            String instanceId,
            Duration lease) {
        this(repository, executor, instanceId, lease, claim -> { });
    }

    public SupplyRuntimeClaimWorker(
            SupplyFulfillmentRepository repository,
            Executor executor,
            String instanceId,
            Duration lease,
            SupplyRuntimeClaimDispatchHandler dispatchHandler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.lease = Objects.requireNonNull(lease, "lease");
        this.dispatchHandler = Objects.requireNonNull(dispatchHandler, "dispatchHandler");
    }

    public CompletableFuture<SupplyRuntimeClaimResult> runOnce() {
        if (closed.get()) return CompletableFuture.completedFuture(SupplyRuntimeClaimResult.CLOSED);
        if (!inFlight.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(SupplyRuntimeClaimResult.ALREADY_RUNNING);
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    if (closed.get()) return SupplyRuntimeClaimResult.CLOSED;
                    Optional<SupplyRuntimeClaim> candidate = repository.claimNext(instanceId, lease);
                    if (candidate.isEmpty()) return SupplyRuntimeClaimResult.NO_WORK;
                    SupplyRuntimeClaim claim = candidate.get();
                    if (claim.shipmentState() == SupplyShipmentState.CREATED) {
                        if (!repository.hasRecoverableRuntimeSnapshot(claim.shipmentId())) {
                            repository.markPendingManual(claim);
                            repository.releaseClaim(claim);
                            return SupplyRuntimeClaimResult.PENDING_MANUAL;
                        }
                        repository.dispatch(claim);
                        dispatchHandler.handle(claim);
                        return SupplyRuntimeClaimResult.DISPATCHED;
                    }
                    repository.releaseClaim(claim);
                    return SupplyRuntimeClaimResult.CLAIMED_NO_MUTATION;
                } catch (SQLException exception) {
                    throw new CompletionException(exception);
                }
            }, executor).whenComplete((ignored, error) -> inFlight.set(false));
        } catch (RuntimeException submissionFailure) {
            inFlight.set(false);
            throw submissionFailure;
        }
    }

    public enum SupplyRuntimeClaimResult {
        CLOSED,
        ALREADY_RUNNING,
        NO_WORK,
        DISPATCHED,
        CLAIMED_NO_MUTATION,
        PENDING_MANUAL
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
