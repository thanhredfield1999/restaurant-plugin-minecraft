package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;

class SupplyRuntimeSessionRegistryProjectionTest {
    @Test
    void replacesProjectionAfterCheckpointCasWithoutDroppingEntitySession() {
        UUID shipmentId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        SupplyRuntimeClaim claim = new SupplyRuntimeClaim(
                shipmentId, packageId, restaurantId, SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT,
                1, "worker", UUID.randomUUID(), Instant.now().plusSeconds(30));
        SupplyDeliveryJourneySnapshot firstSnapshot = snapshot("DELIVERY_ENTRY");
        SupplyDeliveryJourneySnapshot nextSnapshot = snapshot("DELIVERY_STOP");
        SupplyRuntimeProjection first = new SupplyRuntimeProjection(
                shipmentId, packageId, restaurantId, 1, "DELIVERY_ENTRY", 0, firstSnapshot);
        SupplyRuntimeProjection next = new SupplyRuntimeProjection(
                shipmentId, packageId, restaurantId, 2, "DELIVERY_STOP", 0, nextSnapshot);
        SupplyRuntimeSessionRegistry registry = new SupplyRuntimeSessionRegistry(1);

        assertTrue(registry.open(claim, first, entityId));
        assertTrue(registry.replaceProjection(shipmentId, next));
        SupplyRuntimeSession session = registry.get(shipmentId).orElseThrow();
        assertEquals(entityId, session.entityId());
        assertEquals("DELIVERY_STOP", session.projection().checkpointStage());
        assertEquals(2, session.projection().revision());
    }

    private static SupplyDeliveryJourneySnapshot snapshot(String stage) {
        return new SupplyDeliveryJourneySnapshot(1, "fixture", java.util.List.of(
                new SupplyDeliveryJourneyStep(
                        SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new vn.restauranttycoon.supplysetup.SupplySetupPosition("rt-flat-test", 0, 65, 0, 0, 0)),
                new SupplyDeliveryJourneyStep(
                        SupplyDeliveryJourneyStage.DELIVERY_STOP,
                        new vn.restauranttycoon.supplysetup.SupplySetupPosition("rt-flat-test", 1, 65, 0, 0, 0))));
    }
}
