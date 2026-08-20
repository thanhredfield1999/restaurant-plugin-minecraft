package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeMovementTargetResolverTest {
    @Test
    void spawnUsesCurrentCheckpointAndReuseMovesToNextCheckpoint() {
        SupplyDeliveryJourneySnapshot snapshot = snapshot();
        SupplyRuntimeProjection projection = new SupplyRuntimeProjection(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                1, "DELIVERY_ENTRY", 0, snapshot);

        assertEquals(SupplyRuntimeMovementTargetResolver.TargetKind.SPAWN,
                SupplyRuntimeMovementTargetResolver.resolve(projection, false).kind());
        assertEquals(new SupplySetupPosition("world", 10, 64, 0, 0, 0),
                SupplyRuntimeMovementTargetResolver.resolve(projection, true).position());
    }

    @Test
    void terminalCheckpointResolvesMovementTarget() {
        SupplyDeliveryJourneySnapshot snapshot = new SupplyDeliveryJourneySnapshot(1, "owner", java.util.List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_DESPAWN,
                        new SupplySetupPosition("world", 10, 64, 0, 0, 0))));
        SupplyRuntimeProjection projection = new SupplyRuntimeProjection(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                1, "DELIVERY_DESPAWN", 0, snapshot);

        assertEquals(SupplyRuntimeMovementTargetResolver.TargetKind.MOVE,
                SupplyRuntimeMovementTargetResolver.resolve(projection, true).kind());
    }

    private static SupplyDeliveryJourneySnapshot snapshot() {
        return new SupplyDeliveryJourneySnapshot(1, "owner", java.util.List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 0, 64, 0, 0, 0)),
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.UNLOAD_POINT,
                        new SupplySetupPosition("world", 10, 64, 0, 0, 0))));
    }
}
