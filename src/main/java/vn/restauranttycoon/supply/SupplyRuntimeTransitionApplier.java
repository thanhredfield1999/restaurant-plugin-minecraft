package vn.restauranttycoon.supply;

import java.util.Objects;

@FunctionalInterface
interface SupplyRuntimeTransitionExecutor {
    SupplyRuntimeTransitionResult execute(SupplyRuntimeTransitionRequest request) throws Exception;
}

public final class SupplyRuntimeTransitionApplier {
    private final SupplyRuntimeTransitionExecutor executor;

    public SupplyRuntimeTransitionApplier(SupplyRuntimeTransitionExecutor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public SupplyRuntimeTransitionResult apply(
            SupplyRuntimeClaim claim, SupplyRuntimeTransitionCommand command) throws Exception {
        return executor.execute(new SupplyRuntimeTransitionRequest(claim, command));
    }
}
