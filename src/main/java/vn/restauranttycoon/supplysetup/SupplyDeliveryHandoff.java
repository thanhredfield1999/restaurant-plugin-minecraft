package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public final class SupplyDeliveryHandoff {
    private SupplyDeliveryHandoff() {
    }

    public static SupplyDeliveryHandoffResult confirm(
            SupplyDeliveryConvoySimulation convoy,
            SupplyDeliveryPackage deliveryPackage,
            SupplyDeliveryPackageHandoffRequest request
    ) {
        Objects.requireNonNull(convoy, "convoy");
        Objects.requireNonNull(deliveryPackage, "deliveryPackage");
        Objects.requireNonNull(request, "request");
        if (!convoy.plan().owner().equals(deliveryPackage.restaurant())
                || !convoy.plan().owner().equals(request.restaurant())) {
            throw new IllegalArgumentException("convoy and package restaurant do not match");
        }
        if (!convoy.shipmentId().equals(deliveryPackage.shipmentId())
                || !convoy.shipmentId().equals(request.shipmentId())) {
            throw new IllegalArgumentException("convoy and package shipment do not match");
        }
        SupplyDeliveryPackageHandoffResult packageResult = deliveryPackage.handoff(request);
        if (packageResult.duplicate()) {
            SupplyDeliveryConvoySimulation recoveredConvoy;
            if (convoy.state() == SupplyDeliveryConvoyState.WAITING_FOR_HANDOFF) {
                recoveredConvoy = convoy.confirmHandoff();
            } else if (hasCommittedHandoff(convoy)) {
                recoveredConvoy = convoy;
            } else {
                throw new IllegalStateException("convoy has not committed handoff");
            }
            return new SupplyDeliveryHandoffResult(recoveredConvoy, deliveryPackage, true);
        }
        return new SupplyDeliveryHandoffResult(
                convoy.confirmHandoff(),
                packageResult.deliveryPackage(),
                packageResult.duplicate());
    }

    private static boolean hasCommittedHandoff(SupplyDeliveryConvoySimulation convoy) {
        if (convoy.state() == SupplyDeliveryConvoyState.COMPLETED) {
            return true;
        }
        SupplyDeliveryJourneyStage stage = convoy.currentStep().stage();
        return stage == SupplyDeliveryJourneyStage.DELIVERY_EXIT
                || stage == SupplyDeliveryJourneyStage.DELIVERY_DESPAWN;
    }
}
