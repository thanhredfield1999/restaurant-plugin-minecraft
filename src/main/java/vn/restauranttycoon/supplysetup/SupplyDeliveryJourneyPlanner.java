package vn.restauranttycoon.supplysetup;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SupplyDeliveryJourneyPlanner {
    public SupplyDeliveryJourneyPlan plan(
            SupplySetupOwner owner,
            List<SupplySetupPoint> points,
            SupplyRoute route
    ) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(points, "points");
        Objects.requireNonNull(route, "route");
        if (!owner.equals(route.owner())) {
            throw new IllegalArgumentException("route must belong to journey owner");
        }

        Map<SupplySetupPointType, SupplySetupPoint> byType = new EnumMap<>(SupplySetupPointType.class);
        for (SupplySetupPoint point : points) {
            if (!owner.equals(point.owner())) {
                throw new IllegalArgumentException(
                        "all setup points must belong to journey owner");
            }
            if (byType.putIfAbsent(point.type(), point) != null) {
                throw new IllegalArgumentException("duplicate setup point: " + point.type());
            }
        }

        List<SupplyDeliveryJourneyStep> steps = new ArrayList<>();
        steps.add(pointStep(byType, SupplySetupPointType.DELIVERY_ENTRY,
                SupplyDeliveryJourneyStage.DELIVERY_ENTRY));
        for (SupplyRouteWaypoint waypoint : route.waypoints()) {
            steps.add(new SupplyDeliveryJourneyStep(
                    SupplyDeliveryJourneyStage.ROUTE_WAYPOINT,
                    waypoint.position()));
        }
        steps.add(pointStep(byType, SupplySetupPointType.DELIVERY_STOP,
                SupplyDeliveryJourneyStage.DELIVERY_STOP));
        steps.add(pointStep(byType, SupplySetupPointType.UNLOAD_POINT,
                SupplyDeliveryJourneyStage.UNLOAD_POINT));
        steps.add(pointStep(byType, SupplySetupPointType.DELIVERY_EXIT,
                SupplyDeliveryJourneyStage.DELIVERY_EXIT));
        steps.add(pointStep(byType, SupplySetupPointType.DELIVERY_DESPAWN,
                SupplyDeliveryJourneyStage.DELIVERY_DESPAWN));
        String worldName = steps.get(0).position().worldName();
        if (steps.stream().anyMatch(step ->
                !worldName.equals(step.position().worldName()))) {
            throw new IllegalArgumentException("delivery journey cannot cross worlds");
        }
        return new SupplyDeliveryJourneyPlan(owner, steps);
    }

    private static SupplyDeliveryJourneyStep pointStep(
            Map<SupplySetupPointType, SupplySetupPoint> points,
            SupplySetupPointType type,
            SupplyDeliveryJourneyStage stage
    ) {
        SupplySetupPoint point = points.get(type);
        if (point == null) {
            throw new IllegalArgumentException("missing required setup point: " + type);
        }
        return new SupplyDeliveryJourneyStep(stage, point.position());
    }
}
