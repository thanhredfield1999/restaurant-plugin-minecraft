package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeNextCheckpointResolverTest {
    @Test
    void advancesThroughRepeatedRouteWaypoints() {
        SupplyDeliveryJourneySnapshot snapshot = snapshotWithRoute();

        assertEquals(Optional.of(new SupplyRuntimeCheckpoint("ROUTE_WAYPOINT", 0)),
                SupplyRuntimeNextCheckpointResolver.resolve(snapshot, "DELIVERY_ENTRY", 0));
        assertEquals(Optional.of(new SupplyRuntimeCheckpoint("ROUTE_WAYPOINT", 1)),
                SupplyRuntimeNextCheckpointResolver.resolve(snapshot, "ROUTE_WAYPOINT", 0));
        assertEquals(Optional.of(new SupplyRuntimeCheckpoint("DELIVERY_STOP", 0)),
                SupplyRuntimeNextCheckpointResolver.resolve(snapshot, "ROUTE_WAYPOINT", 1));
    }

    @Test
    void terminalCheckpointHasNoNextCheckpoint() {
        assertTrue(SupplyRuntimeNextCheckpointResolver.resolve(
                snapshotWithRoute(), "DELIVERY_DESPAWN", 0).isEmpty());
    }

    @Test
    void emptyRouteSkipsToDeliveryStop() {
        SupplyDeliveryJourneySnapshot snapshot = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, 1),
                step(SupplyDeliveryJourneyStage.DELIVERY_STOP, 2),
                step(SupplyDeliveryJourneyStage.UNLOAD_POINT, 3),
                step(SupplyDeliveryJourneyStage.DELIVERY_EXIT, 4),
                step(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN, 5)));

        assertEquals(Optional.of(new SupplyRuntimeCheckpoint("DELIVERY_STOP", 0)),
                SupplyRuntimeNextCheckpointResolver.resolve(snapshot, "DELIVERY_ENTRY", 0));
    }

    private static SupplyDeliveryJourneySnapshot snapshotWithRoute() {
        return new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                step(SupplyDeliveryJourneyStage.DELIVERY_ENTRY, 1),
                step(SupplyDeliveryJourneyStage.ROUTE_WAYPOINT, 2),
                step(SupplyDeliveryJourneyStage.ROUTE_WAYPOINT, 3),
                step(SupplyDeliveryJourneyStage.DELIVERY_STOP, 4),
                step(SupplyDeliveryJourneyStage.UNLOAD_POINT, 5),
                step(SupplyDeliveryJourneyStage.DELIVERY_EXIT, 6),
                step(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN, 7)));
    }

    private static SupplyDeliveryJourneyStep step(SupplyDeliveryJourneyStage stage, double x) {
        return new SupplyDeliveryJourneyStep(stage,
                new SupplySetupPosition("world", x, 65, 0, 0, 0));
    }
}
