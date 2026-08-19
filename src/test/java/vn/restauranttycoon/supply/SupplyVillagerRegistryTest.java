package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyVillagerRegistryTest {
    @Test
    void boundsCandidatesAndRemovesEmptyShipment() {
        SupplyVillagerRegistry registry = new SupplyVillagerRegistry();
        UUID shipment = UUID.randomUUID();
        assertTrue(registry.register(shipment, UUID.randomUUID()));
        assertTrue(registry.register(shipment, UUID.randomUUID()));
        assertTrue(registry.register(shipment, UUID.randomUUID()));
        assertTrue(registry.register(shipment, UUID.randomUUID()));
        assertFalse(registry.register(shipment, UUID.randomUUID()));
        UUID first = registry.candidates(shipment).get(0);
        registry.unregister(shipment, first);
        assertEquals(3, registry.candidates(shipment).size());
    }
}
