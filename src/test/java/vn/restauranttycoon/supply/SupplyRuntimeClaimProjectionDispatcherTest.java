package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SupplyRuntimeClaimProjectionDispatcherTest {
    @Test
    void mainThreadHandlerReceivesOriginalClaimAndLoadedProjection() throws Exception {
        SupplyRuntimeClaim claim = new SupplyRuntimeClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT,
                1, "worker", UUID.randomUUID(), Instant.now().plusSeconds(30));
        SupplyRuntimeProjection projection = TestSupplyRuntimeProjections.valid(claim.shipmentId(), claim.packageId(), claim.restaurantId());
        AtomicReference<SupplyRuntimeClaim> receivedClaim = new AtomicReference<>();
        AtomicReference<SupplyRuntimeProjection> receivedProjection = new AtomicReference<>();
        SupplyRuntimeClaimProjectionDispatcher dispatcher = new SupplyRuntimeClaimProjectionDispatcher(
                ignored -> Optional.of(projection),
                Runnable::run,
                (received, loaded) -> {
                    receivedClaim.set(received);
                    receivedProjection.set(loaded);
                });

        dispatcher.handle(claim);

        assertEquals(claim, receivedClaim.get());
        assertNotNull(receivedProjection.get());
        assertEquals(projection, receivedProjection.get());
    }
}

final class TestSupplyRuntimeProjections {
    private TestSupplyRuntimeProjections() { }

    static SupplyRuntimeProjection valid(UUID shipment, UUID packageId, UUID restaurantId) {
        var journey = new vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot(
                1, "plot", java.util.List.of(new vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep(
                        vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new vn.restauranttycoon.supplysetup.SupplySetupPosition("world", 0, 64, 0, 0, 0))));
        return new SupplyRuntimeProjection(shipment, packageId, restaurantId, 1,
                "DELIVERY_ENTRY", 0, journey);
    }
}
