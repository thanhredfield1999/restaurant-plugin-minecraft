package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public record SupplyRuntimeFixture(UUID fixtureId, UUID shipmentId, UUID packageId, UUID restaurantId, UUID playerId) {
    public SupplyRuntimeFixture {
        Objects.requireNonNull(fixtureId, "fixtureId");
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(playerId, "playerId");
    }
}
