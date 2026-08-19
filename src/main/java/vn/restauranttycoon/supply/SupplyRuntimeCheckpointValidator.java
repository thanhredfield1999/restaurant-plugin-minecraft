package vn.restauranttycoon.supply;

import java.util.Objects;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;

public final class SupplyRuntimeCheckpointValidator {
    private SupplyRuntimeCheckpointValidator() {
    }

    public static void requirePresent(
            SupplyDeliveryJourneySnapshot snapshot, String stageName, int index) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(stageName, "stageName");
        if (index < 0) throw new IllegalArgumentException("checkpoint index must not be negative");
        SupplyDeliveryJourneyStage stage;
        try {
            stage = SupplyDeliveryJourneyStage.valueOf(stageName);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported checkpoint stage: " + stageName, exception);
        }
        if (stage == SupplyDeliveryJourneyStage.ROUTE_WAYPOINT) {
            int waypointIndex = 0;
            for (var step : snapshot.steps()) {
                if (step.stage() != stage) continue;
                if (waypointIndex++ == index) return;
            }
            throw new IllegalArgumentException("checkpoint waypoint index is absent: " + index);
        }
        if (index != 0) throw new IllegalArgumentException("non-waypoint checkpoint index must be zero");
        if (snapshot.steps().stream().noneMatch(step -> step.stage() == stage)) {
            throw new IllegalArgumentException("checkpoint stage is absent: " + stageName);
        }
    }
}
