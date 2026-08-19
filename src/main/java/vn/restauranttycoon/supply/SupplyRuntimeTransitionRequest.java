package vn.restauranttycoon.supply;

import java.util.Objects;

public record SupplyRuntimeTransitionRequest(
        SupplyRuntimeClaim claim,
        SupplyRuntimeTransitionCommand command) {
    public SupplyRuntimeTransitionRequest {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(command, "command");
    }
}

@FunctionalInterface
interface SupplyRuntimeTransitionStore {
    SupplyRuntimeTransitionResult transition(SupplyRuntimeTransitionRequest request) throws Exception;
}
