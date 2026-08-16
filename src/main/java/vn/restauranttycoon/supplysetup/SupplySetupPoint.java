package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public record SupplySetupPoint(
        SupplySetupOwner owner,
        SupplySetupPointType type,
        SupplySetupPosition position
) {
    public SupplySetupPoint {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(position, "position");
        if (owner.scope() != type.scope()) {
            throw new IllegalArgumentException(
                    "point type " + type + " cannot belong to scope " + owner.scope());
        }
    }
}
