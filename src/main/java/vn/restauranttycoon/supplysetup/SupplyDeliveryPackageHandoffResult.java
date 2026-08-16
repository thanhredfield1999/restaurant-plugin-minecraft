package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public record SupplyDeliveryPackageHandoffResult(
        SupplyDeliveryPackage deliveryPackage,
        boolean duplicate
) {
    public SupplyDeliveryPackageHandoffResult {
        Objects.requireNonNull(deliveryPackage, "deliveryPackage");
        if (deliveryPackage.state() != SupplyDeliveryPackageState.HANDED_OFF) {
            throw new IllegalArgumentException("handoff result package has not been handed off");
        }
    }
}
