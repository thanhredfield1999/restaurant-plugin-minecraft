package vn.restauranttycoon.supply;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SupplyRuntimeCoordinator implements AutoCloseable {
    private final SupplyFulfillmentRepository repository;
    private final Executor executor;
    private final SupplyRuntimeWorkHandler handler;
    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    public SupplyRuntimeCoordinator(
            SupplyFulfillmentRepository repository,
            Executor executor,
            SupplyRuntimeWorkHandler handler) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public boolean poll() {
        if (closed.get()) {
            return false;
        }
        if (!inFlight.compareAndSet(false, true)) {
            return false;
        }
        if (closed.get()) {
            inFlight.set(false);
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    if (closed.get()) {
                        return;
                    }
                    for (SupplyRuntimeWork work : repository.findRuntimeWork(100)) {
                        if (closed.get()) {
                            break;
                        }
                        handler.handle(work);
                    }
                } catch (SQLException | RuntimeException ignored) {
                    // Polling is best-effort; durable state remains available for the next run.
                } finally {
                    inFlight.set(false);
                }
            });
        } catch (RuntimeException submissionFailure) {
            inFlight.set(false);
            throw submissionFailure;
        }
        return true;
    }

    @Override
    public void close() {
        closed.set(true);
    }
}
