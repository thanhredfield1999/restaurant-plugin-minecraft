package vn.restauranttycoon.supply;

import java.time.Instant;
import java.util.UUID;

public record SupplyRuntimeClaim(
        UUID shipmentId,
        UUID packageId,
        UUID restaurantId,
        SupplyShipmentState shipmentState,
        SupplyPackageState packageState,
        int attemptCount,
        String instanceId,
        UUID claimToken,
        Instant claimExpiresAt) {
}
