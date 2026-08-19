package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;

/** Pure next-checkpoint resolver. No Bukkit or database side effects. */
public final class SupplyRuntimeNextCheckpointResolver {
    private static final List<SupplyDeliveryJourneyStage> ORDER = List.of(
            SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
            SupplyDeliveryJourneyStage.ROUTE_WAYPOINT,
            SupplyDeliveryJourneyStage.DELIVERY_STOP,
            SupplyDeliveryJourneyStage.UNLOAD_POINT,
            SupplyDeliveryJourneyStage.DELIVERY_EXIT,
            SupplyDeliveryJourneyStage.DELIVERY_DESPAWN);

    private SupplyRuntimeNextCheckpointResolver() {
    }

    public static Optional<SupplyRuntimeCheckpoint> resolve(
            SupplyDeliveryJourneySnapshot snapshot, String currentStage, int currentIndex) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(currentStage, "currentStage");
        SupplyRuntimeCheckpointValidator.requirePresent(snapshot, currentStage, currentIndex);
        SupplyDeliveryJourneyStage current = parse(currentStage);
        if (current == SupplyDeliveryJourneyStage.DELIVERY_DESPAWN) return Optional.empty();

        if (current == SupplyDeliveryJourneyStage.ROUTE_WAYPOINT) {
            int waypointCount = count(snapshot, SupplyDeliveryJourneyStage.ROUTE_WAYPOINT);
            if (currentIndex + 1 < waypointCount) {
                return Optional.of(new SupplyRuntimeCheckpoint(
                        SupplyDeliveryJourneyStage.ROUTE_WAYPOINT.name(), currentIndex + 1));
            }
        } else if (current == SupplyDeliveryJourneyStage.DELIVERY_ENTRY
                && count(snapshot, SupplyDeliveryJourneyStage.ROUTE_WAYPOINT) > 0) {
            return Optional.of(new SupplyRuntimeCheckpoint(
                    SupplyDeliveryJourneyStage.ROUTE_WAYPOINT.name(), 0));
        }

        int orderIndex = ORDER.indexOf(current);
        for (int i = orderIndex + 1; i < ORDER.size(); i++) {
            SupplyDeliveryJourneyStage candidate = ORDER.get(i);
            if (count(snapshot, candidate) > 0) {
                return Optional.of(new SupplyRuntimeCheckpoint(candidate.name(), 0));
            }
        }
        throw new IllegalArgumentException("journey has no next checkpoint after " + currentStage);
    }

    private static SupplyDeliveryJourneyStage parse(String stage) {
        try {
            return SupplyDeliveryJourneyStage.valueOf(stage);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported checkpoint stage: " + stage, exception);
        }
    }

    private static int count(SupplyDeliveryJourneySnapshot snapshot, SupplyDeliveryJourneyStage stage) {
        return (int) snapshot.steps().stream().filter(step -> step.stage() == stage).count();
    }
}
