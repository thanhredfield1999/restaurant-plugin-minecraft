package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public record SupplyDeliveryHandoffResult(
        SupplyDeliveryConvoySimulation convoy,
        SupplyDeliveryPackage deliveryPackage,
        boolean duplicate
) {
    public SupplyDeliveryHandoffResult {
        Objects.requireNonNull(convoy, "convoy");
        Objects.requireNonNull(deliveryPackage, "deliveryPackage");
        if (!convoy.plan().owner().equals(deliveryPackage.restaurant())) {
            throw new IllegalArgumentException("convoy and package restaurant do not match");
        }
        if (!convoy.shipmentId().equals(deliveryPackage.shipmentId())) {
            throw new IllegalArgumentException("convoy and package shipment do not match");
        }
        if (deliveryPackage.state() != SupplyDeliveryPackageState.HANDED_OFF) {
            throw new IllegalArgumentException("handoff result package has not been handed off");
        }
        if (convoy.state() != SupplyDeliveryConvoyState.COMPLETED
                && convoy.currentStepOptional().map(SupplyDeliveryJourneyStep::stage)
                        .filter(stage -> stage == SupplyDeliveryJourneyStage.DELIVERY_EXIT
                                || stage == SupplyDeliveryJourneyStage.DELIVERY_DESPAWN)
                        .isEmpty()) {
            throw new IllegalArgumentException("handoff result convoy has not committed handoff");
        }
    }
}
