package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

/** Pure transition command builder. Database CAS executes command elsewhere. */
public final class SupplyRuntimeTransitionCommandPlanner {
    private SupplyRuntimeTransitionCommandPlanner() {
    }

    public static SupplyRuntimeTransitionCommand plan(
            SupplyRuntimeProjection projection, UUID operationId) {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(operationId, "operationId");
        return SupplyRuntimeNextCheckpointResolver.resolve(
                        projection.journey(), projection.checkpointStage(), projection.checkpointIndex())
                .map(next -> new SupplyRuntimeTransitionCommand(
                        projection.revision(), projection.checkpointStage(), projection.checkpointIndex(),
                        next.stage(), next.index(), operationId))
                .orElse(null);
    }
}
