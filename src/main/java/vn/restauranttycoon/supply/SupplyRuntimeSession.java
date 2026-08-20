package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public record SupplyRuntimeSession(
        SupplyRuntimeClaim claim,
        SupplyRuntimeProjection projection,
        UUID entityId) {
    public SupplyRuntimeSession {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(entityId, "entityId");
        if (!claim.shipmentId().equals(projection.shipmentId())
                || !claim.packageId().equals(projection.packageId())
                || !claim.restaurantId().equals(projection.restaurantId())) {
            throw new IllegalArgumentException("claim and projection identity mismatch");
        }
    }
}
