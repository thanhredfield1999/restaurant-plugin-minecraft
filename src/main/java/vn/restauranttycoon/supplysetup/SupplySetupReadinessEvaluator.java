package vn.restauranttycoon.supplysetup;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SupplySetupReadinessEvaluator {
    private static final List<SupplySetupPointType> CENTRAL_REQUIRED = List.of(
            SupplySetupPointType.ORDER_DESK,
            SupplySetupPointType.SUPPLIER_SPAWN);
    private static final List<SupplySetupPointType> RESTAURANT_REQUIRED = List.of(
            SupplySetupPointType.DELIVERY_ENTRY,
            SupplySetupPointType.DELIVERY_STOP,
            SupplySetupPointType.UNLOAD_POINT,
            SupplySetupPointType.WAREHOUSE_ENTRY,
            SupplySetupPointType.DELIVERY_EXIT,
            SupplySetupPointType.DELIVERY_DESPAWN);

    public SupplySetupReadiness assess(
            SupplySetupOwner owner,
            List<SupplySetupPoint> points,
            SupplyRoute route
    ) {
        Objects.requireNonNull(owner, "owner");
        List<SupplySetupPoint> pointCopy = List.copyOf(
                Objects.requireNonNull(points, "points"));
        List<SupplyRouteWaypoint> waypoints = route == null
                ? List.of()
                : route.waypoints();

        if (hasForeignOwner(owner, pointCopy, route)) {
            return new SupplySetupReadiness(List.of(
                    SupplySetupReadinessIssue.foreignOwner()));
        }
        if (hasMultipleWorlds(pointCopy, waypoints)) {
            return new SupplySetupReadiness(List.of(
                    SupplySetupReadinessIssue.multipleWorlds()));
        }

        List<SupplySetupReadinessIssue> issues = new ArrayList<>();
        Set<SupplySetupPointType> configuredTypes = EnumSet.noneOf(SupplySetupPointType.class);
        List<SupplySetupPointType> requiredTypes = requiredTypes(owner.scope());
        for (SupplySetupPoint point : pointCopy) {
            if (!configuredTypes.add(point.type())) {
                issues.add(SupplySetupReadinessIssue.duplicate(point.type()));
            }
        }
        for (SupplySetupPointType required : requiredTypes) {
            if (!configuredTypes.contains(required)) {
                issues.add(SupplySetupReadinessIssue.missing(required));
            }
        }
        return new SupplySetupReadiness(issues);
    }

    private static boolean hasForeignOwner(
            SupplySetupOwner owner,
            List<SupplySetupPoint> points,
            SupplyRoute route
    ) {
        for (SupplySetupPoint point : points) {
            if (!owner.equals(point.owner())) {
                return true;
            }
        }
        return route != null && !owner.equals(route.owner());
    }

    private static boolean hasMultipleWorlds(
            List<SupplySetupPoint> points,
            List<SupplyRouteWaypoint> waypoints
    ) {
        Set<String> worlds = new HashSet<>();
        for (SupplySetupPoint point : points) {
            worlds.add(point.position().worldName());
        }
        for (SupplyRouteWaypoint waypoint : waypoints) {
            worlds.add(waypoint.position().worldName());
        }
        return worlds.size() > 1;
    }

    private static List<SupplySetupPointType> requiredTypes(SupplySetupScope scope) {
        return switch (scope) {
            case CENTRAL_SUPPLIER -> CENTRAL_REQUIRED;
            case RESTAURANT -> RESTAURANT_REQUIRED;
        };
    }
}
