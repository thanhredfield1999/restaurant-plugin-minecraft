package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public record SupplyDeliveryJourneyStep(
        SupplyDeliveryJourneyStage stage,
        SupplySetupPosition position
) {
    public SupplyDeliveryJourneyStep {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(position, "position");
    }
}
