package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public record SupplyRouteWaypoint(
        SupplySetupOwner owner,
        int sequence,
        String name,
        SupplySetupPosition position
) {
    public SupplyRouteWaypoint {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        if (owner.scope() != SupplySetupScope.RESTAURANT) {
            throw new IllegalArgumentException("route waypoint must belong to a restaurant");
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("waypoint sequence must be positive");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("waypoint name must not be blank");
        }
    }
}
