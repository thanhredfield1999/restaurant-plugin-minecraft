package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyDeliveryHandoffTest {
    @Test
    void handsOffPackageAndReleasesWaitingConvoy() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID shipmentId = UUID.randomUUID();
        SupplyDeliveryConvoySimulation convoy = waitingConvoy(shipmentId, restaurant);
        UUID packageId = UUID.randomUUID();
        SupplyDeliveryPackage deliveryPackage = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant);
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                shipmentId,
                packageId,
                restaurant,
                UUID.randomUUID());

        SupplyDeliveryHandoffResult result = SupplyDeliveryHandoff.confirm(
                convoy,
                deliveryPackage,
                request);

        assertEquals(SupplyDeliveryPackageState.HANDED_OFF, result.deliveryPackage().state());
        assertEquals(SupplyDeliveryConvoyState.NAVIGATING, result.convoy().state());
        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_EXIT,
                result.convoy().currentStep().stage());
    }

    @Test
    void rejectsResultWhoseConvoyAndPackageBelongToDifferentRestaurants() {
        SupplyDeliveryConvoySimulation convoy = waitingConvoy(
                UUID.randomUUID(),
                SupplySetupOwner.restaurant("plot_alpha"));
        SupplyDeliveryPackage deliveryPackage = SupplyDeliveryPackage.inTransit(
                UUID.randomUUID(),
                UUID.randomUUID(),
                SupplySetupOwner.restaurant("plot_beta"));

        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryHandoffResult(
                convoy,
                deliveryPackage,
                false));
    }

    @Test
    void rejectsResultBeforeConvoyCommitsHandoff() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                shipmentId,
                packageId,
                restaurant,
                UUID.randomUUID());
        SupplyDeliveryPackage handedOff = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant).handoff(request).deliveryPackage();

        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryHandoffResult(
                waitingConvoy(shipmentId, restaurant),
                handedOff,
                false));
    }

    @Test
    void rejectsResultWhosePackageHasNotCommittedHandoff() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID shipmentId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryHandoffResult(
                waitingConvoy(shipmentId, restaurant).confirmHandoff(),
                SupplyDeliveryPackage.inTransit(
                        shipmentId,
                        UUID.randomUUID(),
                        restaurant),
                false));
    }

    @Test
    void returnsCommittedSnapshotsForAnIdenticalAggregateRetry() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                shipmentId,
                packageId,
                restaurant,
                UUID.randomUUID());
        SupplyDeliveryHandoffResult committed = SupplyDeliveryHandoff.confirm(
                waitingConvoy(shipmentId, restaurant),
                SupplyDeliveryPackage.inTransit(shipmentId, packageId, restaurant),
                request);

        SupplyDeliveryHandoffResult retry = SupplyDeliveryHandoff.confirm(
                committed.convoy(),
                committed.deliveryPackage(),
                request);

        assertEquals(committed.convoy(), retry.convoy());
        assertEquals(committed.deliveryPackage(), retry.deliveryPackage());
        assertEquals(true, retry.duplicate());
    }

    @Test
    void releasesWaitingConvoyWhenRetryRecoversPartialAggregateState() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                shipmentId,
                packageId,
                restaurant,
                UUID.randomUUID());
        SupplyDeliveryPackage handedOff = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant).handoff(request).deliveryPackage();

        SupplyDeliveryHandoffResult recovered = SupplyDeliveryHandoff.confirm(
                waitingConvoy(shipmentId, restaurant),
                handedOff,
                request);

        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_EXIT,
                recovered.convoy().currentStep().stage());
        assertEquals(handedOff, recovered.deliveryPackage());
        assertEquals(true, recovered.duplicate());
    }

    @Test
    void rejectsDuplicatePackageBeforeConvoyReachesUnloadPoint() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                shipmentId,
                packageId,
                restaurant,
                UUID.randomUUID());
        SupplyDeliveryPackage handedOff = SupplyDeliveryPackage.inTransit(
                shipmentId,
                packageId,
                restaurant).handoff(request).deliveryPackage();
        SupplyDeliveryConvoySimulation notArrived = SupplyDeliveryConvoySimulation.start(
                shipmentId,
                journeyPlan(restaurant));

        assertThrows(IllegalStateException.class, () -> SupplyDeliveryHandoff.confirm(
                notArrived,
                handedOff,
                request));
    }

    @Test
    void rejectsPackageFromAnotherShipmentOfTheSameRestaurant() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID convoyShipmentId = UUID.randomUUID();
        UUID packageShipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        SupplyDeliveryPackage deliveryPackage = SupplyDeliveryPackage.inTransit(
                packageShipmentId,
                packageId,
                restaurant);
        SupplyDeliveryPackageHandoffRequest request = new SupplyDeliveryPackageHandoffRequest(
                packageShipmentId,
                packageId,
                restaurant,
                UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> SupplyDeliveryHandoff.confirm(
                waitingConvoy(convoyShipmentId, restaurant),
                deliveryPackage,
                request));
    }

    @Test
    void rejectsResultWhoseConvoyAndPackageBelongToDifferentShipments() {
        SupplySetupOwner restaurant = SupplySetupOwner.restaurant("plot_alpha");
        UUID convoyShipmentId = UUID.randomUUID();
        UUID packageShipmentId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryHandoffResult(
                waitingConvoy(convoyShipmentId, restaurant).confirmHandoff(),
                SupplyDeliveryPackage.inTransit(
                        packageShipmentId,
                        UUID.randomUUID(),
                        restaurant),
                false));
    }

    private static SupplyDeliveryConvoySimulation waitingConvoy(
            UUID shipmentId,
            SupplySetupOwner restaurant
    ) {
        SupplyDeliveryConvoySimulation convoy = SupplyDeliveryConvoySimulation.start(
                shipmentId,
                journeyPlan(restaurant));
        convoy = convoy.arriveAtCurrentStep();
        return convoy.arriveAtCurrentStep();
    }

    private static SupplyDeliveryJourneyPlan journeyPlan(SupplySetupOwner restaurant) {
        return new SupplyDeliveryJourneyPlan(restaurant, List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, 10),
                step(SupplyDeliveryJourneyStage.UNLOAD_POINT, 20),
                step(SupplyDeliveryJourneyStage.DELIVERY_EXIT, 30),
                step(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN, 40)));
    }

    private static SupplyDeliveryJourneyStep step(
            SupplyDeliveryJourneyStage stage,
            double x
    ) {
        return new SupplyDeliveryJourneyStep(
                stage,
                new SupplySetupPosition("world", x, 64, 0, 0, 0));
    }
}
