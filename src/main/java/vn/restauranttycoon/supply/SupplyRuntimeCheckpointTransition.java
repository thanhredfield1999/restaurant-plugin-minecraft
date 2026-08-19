package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;

public final class SupplyRuntimeCheckpointTransition {
    private static final List<String> ORDER = List.of(
            "DELIVERY_ENTRY", "ROUTE_WAYPOINT", "DELIVERY_STOP",
            "UNLOAD_POINT", "DELIVERY_EXIT", "DELIVERY_DESPAWN");

    private SupplyRuntimeCheckpointTransition() {
    }

    public static boolean isNext(String current, String next) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(next, "next");
        if ("PENDING_MANUAL".equals(current) || "PENDING_MANUAL".equals(next)) return false;
        if ("DELIVERY_ENTRY".equals(current)) {
            return "ROUTE_WAYPOINT".equals(next) || "DELIVERY_STOP".equals(next);
        }
        if ("ROUTE_WAYPOINT".equals(current)) {
            return "ROUTE_WAYPOINT".equals(next) || "DELIVERY_STOP".equals(next);
        }
        int currentIndex = ORDER.indexOf(current);
        int nextIndex = ORDER.indexOf(next);
        return currentIndex >= 0 && nextIndex == currentIndex + 1;
    }

    public static void requireNext(String current, String next) {
        if (!isNext(current, next)) {
            throw new IllegalArgumentException("invalid checkpoint transition: " + current + " -> " + next);
        }
    }

    public static void requireNext(
            SupplyDeliveryJourneySnapshot snapshot,
            String current, int currentIndex, String next, int nextIndex) {
        Objects.requireNonNull(snapshot, "snapshot");
        requireNext(current, next);
        SupplyRuntimeCheckpointValidator.requirePresent(snapshot, current, currentIndex);
        SupplyRuntimeCheckpointValidator.requirePresent(snapshot, next, nextIndex);
        if ("DELIVERY_ENTRY".equals(current) && "DELIVERY_STOP".equals(next)
                && snapshot.steps().stream().anyMatch(step -> step.stage().name().equals("ROUTE_WAYPOINT"))) {
            throw new IllegalArgumentException("cannot skip configured route waypoints");
        }
        if ("ROUTE_WAYPOINT".equals(current) && "DELIVERY_STOP".equals(next)) {
            int waypointCount = (int) snapshot.steps().stream()
                    .filter(step -> "ROUTE_WAYPOINT".equals(step.stage().name())).count();
            if (currentIndex != waypointCount - 1) {
                throw new IllegalArgumentException("cannot skip remaining route waypoints");
            }
        }
    }
}
