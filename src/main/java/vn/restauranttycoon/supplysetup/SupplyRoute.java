package vn.restauranttycoon.supplysetup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SupplyRoute {
    public static final int MAX_WAYPOINTS = 7;

    private final SupplySetupOwner owner;
    private final List<SupplyRouteWaypoint> waypoints;

    public SupplyRoute(List<SupplyRouteWaypoint> waypoints) {
        Objects.requireNonNull(waypoints, "waypoints");
        List<SupplyRouteWaypoint> copy = List.copyOf(waypoints);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("empty route requires an explicit restaurant owner");
        }
        this.owner = copy.get(0).owner();
        this.waypoints = validate(owner, copy);
    }

    public SupplyRoute(SupplySetupOwner owner, List<SupplyRouteWaypoint> waypoints) {
        this.owner = Objects.requireNonNull(owner, "owner");
        if (owner.scope() != SupplySetupScope.RESTAURANT) {
            throw new IllegalArgumentException("routes can only belong to a restaurant");
        }
        this.waypoints = validate(owner, List.copyOf(
                Objects.requireNonNull(waypoints, "waypoints")));
    }

    private static List<SupplyRouteWaypoint> validate(
            SupplySetupOwner owner,
            List<SupplyRouteWaypoint> waypoints
    ) {
        List<SupplyRouteWaypoint> copy = List.copyOf(waypoints);
        if (copy.size() > MAX_WAYPOINTS) {
            throw new IllegalArgumentException("route cannot contain more than 7 waypoints");
        }
        for (int index = 0; index < copy.size(); index++) {
            SupplyRouteWaypoint waypoint = copy.get(index);
            if (!owner.equals(waypoint.owner())) {
                throw new IllegalArgumentException(
                        "all waypoints must belong to the same restaurant");
            }
            if (waypoint.sequence() != index + 1) {
                throw new IllegalArgumentException(
                        "waypoint sequence must be contiguous from 1");
            }
        }
        return List.copyOf(new ArrayList<>(copy));
    }

    public SupplySetupOwner owner() {
        return owner;
    }

    public List<SupplyRouteWaypoint> waypoints() {
        return waypoints;
    }
}
