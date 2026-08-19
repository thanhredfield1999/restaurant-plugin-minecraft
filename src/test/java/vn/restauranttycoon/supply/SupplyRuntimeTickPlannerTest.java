package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeTickPlannerTest {
    private static final UUID SHIPMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void missingEntityRequestsSpawnAtPersistedCheckpoint() {
        SupplyRuntimeProjection projection = projection();

        SupplyRuntimeTickPlan plan = SupplyRuntimeTickPlanner.plan(
                projection, List.of(), SupplyVillagerMovementOutcome.MOVING);

        assertEquals(SupplyRuntimeTickAction.SPAWN, plan.action());
        assertEquals(new SupplySetupPosition("world", 4, 65, 6, 90, 0), plan.target());
    }

    @Test
    void matchingEntityContinuesMovementAndArrivalTransitionsCheckpoint() {
        SupplyRuntimeProjection projection = projection();

        SupplyRuntimeTickPlan moving = SupplyRuntimeTickPlanner.plan(
                projection, List.of(UUID.randomUUID()), SupplyVillagerMovementOutcome.MOVING);
        SupplyRuntimeTickPlan arrived = SupplyRuntimeTickPlanner.plan(
                projection, List.of(UUID.randomUUID()), SupplyVillagerMovementOutcome.ARRIVED);

        assertEquals(SupplyRuntimeTickAction.MOVE, moving.action());
        assertEquals(SupplyRuntimeTickAction.TRANSITION_CHECKPOINT, arrived.action());
    }

    @Test
    void duplicateEntitiesFailClosed() {
        SupplyRuntimeTickPlan plan = SupplyRuntimeTickPlanner.plan(
                projection(), List.of(SHIPMENT_ID, SHIPMENT_ID), SupplyVillagerMovementOutcome.MOVING);

        assertEquals(SupplyRuntimeTickAction.MARK_PENDING_MANUAL, plan.action());
    }

    private static SupplyRuntimeProjection projection() {
        SupplyDeliveryJourneySnapshot journey = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(
                        SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 4, 65, 6, 90, 0)),
                new SupplyDeliveryJourneyStep(
                        SupplyDeliveryJourneyStage.DELIVERY_STOP,
                        new SupplySetupPosition("world", 8, 65, 6, 90, 0))));
        return new SupplyRuntimeProjection(
                SHIPMENT_ID, UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                1, "DELIVERY_ENTRY", 0, journey);
    }
}
