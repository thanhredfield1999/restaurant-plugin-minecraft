package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

/** Pure server-thread tick decision; entity and database side effects stay outside. */
public final class SupplyRuntimeTickPlanner {
    private SupplyRuntimeTickPlanner() {
    }

    public static SupplyRuntimeTickPlan plan(
            SupplyRuntimeProjection projection,
            List<UUID> entityCandidates,
            SupplyVillagerMovementOutcome movementOutcome) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(entityCandidates, "entityCandidates");
        Objects.requireNonNull(movementOutcome, "movementOutcome");
        SupplySetupPosition target = SupplyRuntimeCheckpointResolver.resolve(
                projection.journey(), projection.checkpointStage(), projection.checkpointIndex());
        SupplyVillagerRecoveryDecision.Decision recovery = SupplyVillagerRecoveryDecision.decide(
                projection.shipmentId(), entityCandidates);
        if (recovery == SupplyVillagerRecoveryDecision.Decision.DUPLICATE) {
            return new SupplyRuntimeTickPlan(SupplyRuntimeTickAction.MARK_PENDING_MANUAL, null);
        }
        if (recovery == SupplyVillagerRecoveryDecision.Decision.SPAWN) {
            return new SupplyRuntimeTickPlan(SupplyRuntimeTickAction.SPAWN, target);
        }
        return switch (movementOutcome) {
            case MOVING -> new SupplyRuntimeTickPlan(SupplyRuntimeTickAction.MOVE, target);
            case ARRIVED -> new SupplyRuntimeTickPlan(SupplyRuntimeTickAction.TRANSITION_CHECKPOINT, target);
            case STUCK -> new SupplyRuntimeTickPlan(SupplyRuntimeTickAction.MARK_PENDING_MANUAL, null);
        };
    }
}
