package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public record SupplyFulfillmentRecord(
        UUID shipmentId,
        UUID packageId,
        SupplyShipmentState shipmentState,
        SupplyPackageState packageState) {
    public SupplyFulfillmentRecord {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(shipmentState, "shipmentState");
        Objects.requireNonNull(packageState, "packageState");
    }
}
