package vn.restauranttycoon.supplysetup;

import java.util.Objects;
import java.util.UUID;

public record SupplyDeliveryPackageHandoffRequest(
        UUID shipmentId,
        UUID packageId,
        SupplySetupOwner restaurant,
        UUID operationId
) {
    public SupplyDeliveryPackageHandoffRequest {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurant, "restaurant");
        Objects.requireNonNull(operationId, "operationId");
        if (restaurant.scope() != SupplySetupScope.RESTAURANT) {
            throw new IllegalArgumentException("handoff restaurant must use restaurant scope");
        }
    }
}
