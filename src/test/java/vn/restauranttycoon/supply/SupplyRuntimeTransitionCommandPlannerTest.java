package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeTransitionCommandPlannerTest {
    private static final UUID OPERATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Test
    void createsCommandForNextCheckpointWithExpectedRevision() {
        SupplyRuntimeProjection projection = projection("DELIVERY_ENTRY", 0, 7);

        SupplyRuntimeTransitionCommand command = SupplyRuntimeTransitionCommandPlanner.plan(
                projection, OPERATION_ID);

        assertEquals(7, command.expectedRevision());
        assertEquals("DELIVERY_ENTRY", command.expectedStage());
        assertEquals(0, command.expectedIndex());
        assertEquals("ROUTE_WAYPOINT", command.nextStage());
        assertEquals(0, command.nextIndex());
        assertEquals(OPERATION_ID, command.operationId());
    }

    @Test
    void terminalCheckpointProducesNoCommand() {
        SupplyRuntimeProjection projection = projection("DELIVERY_DESPAWN", 0, 9);

        assertEquals(null, SupplyRuntimeTransitionCommandPlanner.plan(projection, OPERATION_ID));
    }

    @Test
    void malformedProjectionFailsClosed() {
        SupplyDeliveryJourneySnapshot snapshot = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 1, 65, 1, 0, 0))));
        SupplyRuntimeProjection projection = new SupplyRuntimeProjection(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "DELIVERY_ENTRY", 0, snapshot);

        assertThrows(IllegalArgumentException.class,
                () -> SupplyRuntimeTransitionCommandPlanner.plan(projection, OPERATION_ID));
    }

    private static SupplyRuntimeProjection projection(String stage, int index, long revision) {
        SupplyDeliveryJourneySnapshot snapshot = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, 1),
                step(SupplyDeliveryJourneyStage.ROUTE_WAYPOINT, 2),
                step(SupplyDeliveryJourneyStage.DELIVERY_STOP, 3),
                step(SupplyDeliveryJourneyStage.UNLOAD_POINT, 4),
                step(SupplyDeliveryJourneyStage.DELIVERY_EXIT, 5),
                step(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN, 6)));
        return new SupplyRuntimeProjection(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), revision,
                stage, index, snapshot);
    }

    private static SupplyDeliveryJourneyStep step(SupplyDeliveryJourneyStage stage, double x) {
        return new SupplyDeliveryJourneyStep(stage,
                new SupplySetupPosition("world", x, 65, 0, 0, 0));
    }
}
