package vn.restauranttycoon.supply;

import java.util.Objects;

public final class SupplyRuntimeMovementActionResolver {
    private SupplyRuntimeMovementActionResolver() {
    }

    public static SupplyRuntimeMovementAction resolve(
            SupplyVillagerMovementOutcome outcome,
            String checkpointStage) {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(checkpointStage, "checkpointStage");
        return switch (outcome) {
            case MOVING -> SupplyRuntimeMovementAction.CONTINUE_MOVING;
            case STUCK -> SupplyRuntimeMovementAction.MARK_PENDING_MANUAL;
            case ARRIVED -> "DELIVERY_DESPAWN".equals(checkpointStage)
                    ? SupplyRuntimeMovementAction.FINALIZE_AND_CLEANUP
                    : SupplyRuntimeMovementAction.TRANSITION_CHECKPOINT;
        };
    }
}
