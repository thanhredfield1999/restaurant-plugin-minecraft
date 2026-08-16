package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class SupplyDeliveryConvoySimulationTest {
    private static final UUID SHIPMENT_ID = UUID.randomUUID();

    @Test
    void waitsForHandoffAfterReachingUnloadPoint() {
        SupplyDeliveryConvoySimulation simulation =
                SupplyDeliveryConvoySimulation.start(SHIPMENT_ID, journeyPlan());

        assertEquals(SHIPMENT_ID, simulation.shipmentId());
        assertEquals(SupplyDeliveryConvoyState.NAVIGATING, simulation.state());
        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                simulation.currentStep().stage());

        simulation = simulation.arriveAtCurrentStep();
        assertEquals(SupplyDeliveryJourneyStage.ROUTE_WAYPOINT,
                simulation.currentStep().stage());

        simulation = simulation.arriveAtCurrentStep();
        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_STOP,
                simulation.currentStep().stage());

        simulation = simulation.arriveAtCurrentStep();
        assertEquals(SupplyDeliveryJourneyStage.UNLOAD_POINT,
                simulation.currentStep().stage());

        simulation = simulation.arriveAtCurrentStep();
        assertEquals(SupplyDeliveryConvoyState.WAITING_FOR_HANDOFF, simulation.state());
        assertEquals(SupplyDeliveryJourneyStage.UNLOAD_POINT,
                simulation.currentStep().stage());
    }

    @Test
    void continuesToDeliveryExitAfterHandoff() {
        SupplyDeliveryConvoySimulation simulation = waitingForHandoff();

        simulation = simulation.confirmHandoff();

        assertEquals(SupplyDeliveryConvoyState.NAVIGATING, simulation.state());
        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_EXIT,
                simulation.currentStep().stage());
    }

    @Test
    void rejectsHandoffBeforeUnloadPoint() {
        SupplyDeliveryConvoySimulation simulation =
                SupplyDeliveryConvoySimulation.start(SHIPMENT_ID, journeyPlan());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                simulation::confirmHandoff);

        assertEquals("convoy is not waiting for handoff", error.getMessage());
    }

    @Test
    void rejectsEmptyJourneyPlan() {
        SupplyDeliveryJourneyPlan emptyPlan = new SupplyDeliveryJourneyPlan(
                SupplySetupOwner.restaurant("plot_1"),
                List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SupplyDeliveryConvoySimulation.start(SHIPMENT_ID, emptyPlan));

        assertEquals("journey plan must contain steps", error.getMessage());
    }

    @Test
    void completesAfterReachingDeliveryDespawn() {
        SupplyDeliveryConvoySimulation simulation =
                waitingForHandoff().confirmHandoff();
        simulation = simulation.arriveAtCurrentStep();
        assertEquals(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN,
                simulation.currentStep().stage());

        simulation = simulation.arriveAtCurrentStep();

        assertEquals(SupplyDeliveryConvoyState.COMPLETED, simulation.state());
        assertTrue(simulation.currentStepOptional().isEmpty());
        SupplyDeliveryConvoySimulation completed = simulation;
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                completed::arriveAtCurrentStep);
        assertEquals("convoy is not navigating", error.getMessage());
    }

    @Test
    void rejectsSnapshotWhoseStepIndexIsOutsideThePlan() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryConvoySimulation(
                SHIPMENT_ID,
                journeyPlan(),
                -1,
                SupplyDeliveryConvoyState.NAVIGATING));
        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryConvoySimulation(
                SHIPMENT_ID,
                journeyPlan(),
                journeyPlan().steps().size() + 1,
                SupplyDeliveryConvoyState.COMPLETED));
    }

    @Test
    void rejectsCompletedSnapshotBeforeTheJourneyEnds() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryConvoySimulation(
                SHIPMENT_ID,
                journeyPlan(),
                0,
                SupplyDeliveryConvoyState.COMPLETED));
    }

    @Test
    void rejectsActiveSnapshotAfterTheJourneyEnds() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryConvoySimulation(
                SHIPMENT_ID,
                journeyPlan(),
                journeyPlan().steps().size(),
                SupplyDeliveryConvoyState.NAVIGATING));
    }

    @Test
    void rejectsWaitingSnapshotOutsideTheUnloadPoint() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyDeliveryConvoySimulation(
                SHIPMENT_ID,
                journeyPlan(),
                0,
                SupplyDeliveryConvoyState.WAITING_FOR_HANDOFF));
    }

    private static SupplyDeliveryConvoySimulation waitingForHandoff() {
        SupplyDeliveryConvoySimulation simulation =
                SupplyDeliveryConvoySimulation.start(SHIPMENT_ID, journeyPlan());
        while (simulation.currentStep().stage()
                != SupplyDeliveryJourneyStage.UNLOAD_POINT) {
            simulation = simulation.arriveAtCurrentStep();
        }
        return simulation.arriveAtCurrentStep();
    }

    private static SupplyDeliveryJourneyPlan journeyPlan() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        return new SupplyDeliveryJourneyPlan(owner, List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, 10),
                step(SupplyDeliveryJourneyStage.ROUTE_WAYPOINT, 20),
                step(SupplyDeliveryJourneyStage.DELIVERY_STOP, 30),
                step(SupplyDeliveryJourneyStage.UNLOAD_POINT, 40),
                step(SupplyDeliveryJourneyStage.DELIVERY_EXIT, 50),
                step(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN, 60)));
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
