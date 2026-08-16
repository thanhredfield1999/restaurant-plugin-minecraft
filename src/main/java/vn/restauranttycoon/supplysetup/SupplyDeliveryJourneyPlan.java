package vn.restauranttycoon.supplysetup;

import java.util.List;
import java.util.Objects;

public record SupplyDeliveryJourneyPlan(
        SupplySetupOwner owner,
        List<SupplyDeliveryJourneyStep> steps
) {
    public SupplyDeliveryJourneyPlan {
        Objects.requireNonNull(owner, "owner");
        if (owner.scope() != SupplySetupScope.RESTAURANT) {
            throw new IllegalArgumentException("delivery journey must belong to a restaurant");
        }
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }
}
