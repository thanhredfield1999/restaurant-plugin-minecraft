package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyRuntimeSessionRegistryTest {
    @Test
    void registersOneSessionPerShipmentAndPreservesClaimIdentity() {
        SupplyRuntimeSessionRegistry registry = new SupplyRuntimeSessionRegistry(2);
        SupplyRuntimeClaim claim = claim();
        SupplyRuntimeProjection projection = projection(claim);
        UUID entity = UUID.randomUUID();

        assertTrue(registry.open(claim, projection, entity));
        assertEquals(new SupplyRuntimeSession(claim, projection, entity), registry.get(claim.shipmentId()).orElseThrow());
        assertFalse(registry.open(claim, projection, UUID.randomUUID()));
    }

    @Test
    void boundedRegistryRejectsAdditionalShipments() {
        SupplyRuntimeSessionRegistry registry = new SupplyRuntimeSessionRegistry(1);
        SupplyRuntimeClaim first = claim();
        SupplyRuntimeClaim second = claim();

        assertTrue(registry.open(first, projection(first), UUID.randomUUID()));
        assertFalse(registry.open(second, projection(second), UUID.randomUUID()));
        assertEquals(1, registry.size());
    }

    private static SupplyRuntimeClaim claim() {
        return new SupplyRuntimeClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT,
                1, "worker", UUID.randomUUID(), Instant.now().plusSeconds(30));
    }

    private static SupplyRuntimeProjection projection(SupplyRuntimeClaim claim) {
        var journey = new vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot(1, "plot",
                java.util.List.of(new vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep(
                        vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new vn.restauranttycoon.supplysetup.SupplySetupPosition("world", 0, 64, 0, 0, 0))));
        return new SupplyRuntimeProjection(claim.shipmentId(), claim.packageId(), claim.restaurantId(),
                1, "DELIVERY_ENTRY", 0, journey);
    }
}
