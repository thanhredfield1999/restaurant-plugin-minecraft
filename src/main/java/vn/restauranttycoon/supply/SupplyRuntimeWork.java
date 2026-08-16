package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public record SupplyRuntimeWork(
        UUID shipmentId,
        UUID packageId,
        UUID restaurantId,
        SupplyShipmentState shipmentState,
        SupplyPackageState packageState) {
    public SupplyRuntimeWork {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        Objects.requireNonNull(shipmentState, "shipmentState");
        Objects.requireNonNull(packageState, "packageState");
    }
}
