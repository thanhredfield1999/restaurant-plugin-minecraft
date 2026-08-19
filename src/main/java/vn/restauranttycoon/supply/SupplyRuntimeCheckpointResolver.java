package vn.restauranttycoon.supply;

import java.util.Objects;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

public final class SupplyRuntimeCheckpointResolver {
    private SupplyRuntimeCheckpointResolver() {
    }

    public static SupplySetupPosition resolve(
            SupplyDeliveryJourneySnapshot snapshot,
            String checkpointStage
    ) {
        return resolve(snapshot, checkpointStage, 0);
    }

    public static SupplySetupPosition resolve(
            SupplyDeliveryJourneySnapshot snapshot,
            String checkpointStage,
            int checkpointIndex
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(checkpointStage, "checkpointStage");
        final SupplyDeliveryJourneyStage stage;
        try {
            stage = SupplyDeliveryJourneyStage.valueOf(checkpointStage);
        } catch (IllegalArgumentException invalidStage) {
            throw new IllegalArgumentException("unsupported runtime checkpoint: " + checkpointStage, invalidStage);
        }
        if (checkpointIndex < 0) throw new IllegalArgumentException("checkpointIndex must not be negative");
        if (stage == SupplyDeliveryJourneyStage.ROUTE_WAYPOINT) {
            int waypointIndex = 0;
            for (SupplyDeliveryJourneyStep step : snapshot.steps()) {
                if (step.stage() != stage) continue;
                if (waypointIndex++ == checkpointIndex) return step.position();
            }
            throw new IllegalArgumentException("route waypoint index is absent: " + checkpointIndex);
        }
        if (checkpointIndex != 0) throw new IllegalArgumentException("non-waypoint checkpoint index must be zero");
        for (SupplyDeliveryJourneyStep step : snapshot.steps()) {
            if (step.stage() == stage) return step.position();
        }
        throw new IllegalArgumentException("checkpoint is absent from journey snapshot: " + checkpointStage);
    }
}
