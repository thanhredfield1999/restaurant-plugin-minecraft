package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import java.util.List;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeClaimProjectionSourceTest {
    @Test
    void convertsClaimToWorkAndLoadsProjection() throws Exception {
        SupplyRuntimeClaim claim = new SupplyRuntimeClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT,
                1, "runtime-a", UUID.randomUUID(), java.time.Instant.now().plusSeconds(30));
        SupplyRuntimeProjection projection = new SupplyRuntimeProjection(
                claim.shipmentId(), claim.packageId(), claim.restaurantId(), 1,
                "DELIVERY_ENTRY", 0, journey());

        SupplyRuntimeClaimProjectionSource source = new SupplyRuntimeClaimProjectionSource(
                work -> {
                    assertEquals(claim.shipmentId(), work.shipmentId());
                    assertEquals(claim.packageId(), work.packageId());
                    assertEquals(claim.restaurantId(), work.restaurantId());
                    return Optional.of(projection);
                });

        assertEquals(Optional.of(projection), source.load(claim));
    }

    private static SupplyDeliveryJourneySnapshot journey() {
        return new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 1, 65, 1, 0, 0))));
    }

    @Test
    void missingProjectionStaysEmpty() throws Exception {
        SupplyRuntimeClaimProjectionSource source = new SupplyRuntimeClaimProjectionSource(
                work -> Optional.empty());
        SupplyRuntimeClaim claim = new SupplyRuntimeClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT,
                1, "runtime-a", UUID.randomUUID(), java.time.Instant.now().plusSeconds(30));

        assertTrue(source.load(claim).isEmpty());
    }
}
