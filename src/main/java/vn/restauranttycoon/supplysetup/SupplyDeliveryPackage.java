package vn.restauranttycoon.supplysetup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SupplyDeliveryPackage(
        UUID shipmentId,
        UUID packageId,
        SupplySetupOwner restaurant,
        SupplyDeliveryPackageState state,
        Optional<UUID> handoffOperationId,
        long stateRevision
) {
    public SupplyDeliveryPackage {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(packageId, "packageId");
        Objects.requireNonNull(restaurant, "restaurant");
        Objects.requireNonNull(state, "state");
        handoffOperationId = Objects.requireNonNull(handoffOperationId, "handoffOperationId");
        if (restaurant.scope() != SupplySetupScope.RESTAURANT) {
            throw new IllegalArgumentException("delivery package must belong to a restaurant");
        }
        if (state == SupplyDeliveryPackageState.IN_TRANSIT
                && (handoffOperationId.isPresent() || stateRevision != 0)) {
            throw new IllegalArgumentException("in-transit package cannot have handoff state");
        }
        if (state == SupplyDeliveryPackageState.HANDED_OFF
                && (handoffOperationId.isEmpty() || stateRevision != 1)) {
            throw new IllegalArgumentException("handed-off package must identify its handoff");
        }
    }

    public static SupplyDeliveryPackage inTransit(
            UUID shipmentId,
            UUID packageId,
            SupplySetupOwner restaurant
    ) {
        return new SupplyDeliveryPackage(
                shipmentId,
                packageId,
                restaurant,
                SupplyDeliveryPackageState.IN_TRANSIT,
                Optional.empty(),
                0);
    }

    public SupplyDeliveryPackageHandoffResult handoff(
            SupplyDeliveryPackageHandoffRequest request
    ) {
        Objects.requireNonNull(request, "request");
        if (!shipmentId.equals(request.shipmentId())
                || !packageId.equals(request.packageId())
                || !restaurant.equals(request.restaurant())) {
            throw new IllegalArgumentException("handoff identity does not match delivery package");
        }
        if (state == SupplyDeliveryPackageState.HANDED_OFF
                && handoffOperationId.equals(Optional.of(request.operationId()))) {
            return new SupplyDeliveryPackageHandoffResult(this, true);
        }
        if (state != SupplyDeliveryPackageState.IN_TRANSIT) {
            throw new IllegalStateException("delivery package has already been handed off");
        }
        SupplyDeliveryPackage handedOff = new SupplyDeliveryPackage(
                shipmentId,
                packageId,
                restaurant,
                SupplyDeliveryPackageState.HANDED_OFF,
                Optional.of(request.operationId()),
                Math.addExact(stateRevision, 1));
        return new SupplyDeliveryPackageHandoffResult(handedOff, false);
    }
}
