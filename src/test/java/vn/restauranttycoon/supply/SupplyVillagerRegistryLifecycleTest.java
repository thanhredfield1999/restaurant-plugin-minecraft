package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyVillagerRegistryLifecycleTest {
    @Test
    void clearRemovesAllCandidatesForPluginShutdown() {
        SupplyVillagerRegistry registry = new SupplyVillagerRegistry();
        UUID shipmentId = UUID.randomUUID();
        registry.register(shipmentId, UUID.randomUUID());
        registry.clear();
        assertTrue(registry.candidates(shipmentId).isEmpty());
    }
}
