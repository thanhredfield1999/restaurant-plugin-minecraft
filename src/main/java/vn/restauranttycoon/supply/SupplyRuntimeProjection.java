package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;

public record SupplyRuntimeProjection(
        UUID shipmentId,
        UUID packageId,
        UUID restaurantId,
        long revision,
        String checkpointStage,
        int checkpointIndex,
        SupplyDeliveryJourneySnapshot journey
) {
    public SupplyRuntimeProjection {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        if (checkpointIndex < 0) throw new IllegalArgumentException("checkpointIndex must not be negative");
        Objects.requireNonNull(checkpointStage, "checkpointStage");
        Objects.requireNonNull(journey, "journey");
        SupplyRuntimeCheckpointValidator.requirePresent(journey, checkpointStage, checkpointIndex);
    }
}
