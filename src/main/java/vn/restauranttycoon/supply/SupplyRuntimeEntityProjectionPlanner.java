package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SupplyRuntimeEntityProjectionPlanner {
    private SupplyRuntimeEntityProjectionPlanner() {
    }

    public static SupplyRuntimeEntityProjectionPlan plan(
            UUID shipmentId, List<UUID> entityCandidates) {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(entityCandidates, "entityCandidates");
        entityCandidates.forEach(candidate -> Objects.requireNonNull(candidate, "entityCandidate"));
        if (entityCandidates.size() > 1) {
            return new SupplyRuntimeEntityProjectionPlan(
                    SupplyRuntimeEntityProjectionPlan.Action.PENDING_MANUAL, null);
        }
        if (entityCandidates.isEmpty()) {
            return new SupplyRuntimeEntityProjectionPlan(
                    SupplyRuntimeEntityProjectionPlan.Action.SPAWN, null);
        }
        return new SupplyRuntimeEntityProjectionPlan(
                SupplyRuntimeEntityProjectionPlan.Action.REUSE, entityCandidates.get(0));
    }
}
