package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.concurrent.Executor;

/** Chuyen work tu DB executor sang server thread; khong tu dong mutate DB. */
public final class SupplyRuntimeMainThreadBridge implements SupplyRuntimeWorkHandler {
    private final Executor mainThreadExecutor;
    private final SupplyRuntimeWorkHandler mainThreadHandler;

    public SupplyRuntimeMainThreadBridge(Executor mainThreadExecutor, SupplyRuntimeWorkHandler mainThreadHandler) {
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.mainThreadHandler = Objects.requireNonNull(mainThreadHandler, "mainThreadHandler");
    }

    @Override
    public void handle(SupplyRuntimeWork work) {
        Objects.requireNonNull(work, "work");
        mainThreadExecutor.execute(() -> mainThreadHandler.handle(work));
    }
}
