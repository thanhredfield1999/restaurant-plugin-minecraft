package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SupplyDeliveryPackageTest {
    @Test
    void handsOffAnInTransitPackageForMatchingIdentity() {
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        SupplyDeliveryPackage deliveryPackage = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant);

        SupplyDeliveryPackageHandoffResult result = deliveryPackage.handoff(
                new SupplyDeliveryPackageHandoffRequest(
                        shipmentId,
                        packageId,
                        restaurant,
                        operationId));

        assertEquals(SupplyDeliveryPackageState.HANDED_OFF, result.deliveryPackage().state());
        assertEquals(1, result.deliveryPackage().stateRevision());
        assertEquals(operationId, result.deliveryPackage().handoffOperationId().orElseThrow());
        assertFalse(result.duplicate());
    }

    @Test
    void returnsTheCommittedPackageForAnIdenticalHandoffRetry() {
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID operationId = UUID.randomUUID();
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                shipmentId,
                packageId,
                restaurant,
                operationId);
        SupplyDeliveryPackage handedOff = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant).handoff(request).deliveryPackage();

        SupplyDeliveryPackageHandoffResult retry = handedOff.handoff(request);

        assertEquals(handedOff, retry.deliveryPackage());
        assertEquals(1, retry.deliveryPackage().stateRevision());
        assertEquals(true, retry.duplicate());
    }

    @Test
    void rejectsASecondHandoffOperationAfterCommit() {
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        SupplyDeliveryPackage handedOff = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant).handoff(new SupplyDeliveryPackageHandoffRequest(
                        shipmentId,
                        packageId,
                        restaurant,
                        UUID.randomUUID())).deliveryPackage();

        assertThrows(IllegalStateException.class, () -> handedOff.handoff(
                new SupplyDeliveryPackageHandoffRequest(
                        shipmentId,
                        packageId,
                        restaurant,
                        UUID.randomUUID())));
        assertEquals(1, handedOff.stateRevision());
    }

    @Test
    void rejectsHandedOffSnapshotWithoutOperationIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryPackage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SupplySetupOwner.restaurant("plot_alpha"),
                SupplyDeliveryPackageState.HANDED_OFF,
                Optional.empty(),
                1));
    }

    @Test
    void rejectsHandoffResultWhosePackageIsStillInTransit() {
        SupplyDeliveryPackage deliveryPackage = SupplyDeliveryPackage.inTransit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SupplySetupOwner.restaurant("plot_alpha"));

        assertThrows(IllegalArgumentException.class, () ->
                new SupplyDeliveryPackageHandoffResult(deliveryPackage, false));
    }
}
