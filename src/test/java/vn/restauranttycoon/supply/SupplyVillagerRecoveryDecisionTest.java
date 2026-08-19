package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyVillagerRecoveryDecisionTest {
    private final UUID shipment = UUID.randomUUID();

    @Test
    void missingCandidateRequestsSpawn() {
        assertEquals(SupplyVillagerRecoveryDecision.Decision.SPAWN,
                SupplyVillagerRecoveryDecision.decide(shipment, List.of()));
    }

    @Test
    void oneEntityCandidateReusesExistingProjection() {
        assertEquals(SupplyVillagerRecoveryDecision.Decision.REUSE,
                SupplyVillagerRecoveryDecision.decide(shipment, List.of(UUID.randomUUID())));
    }

    @Test
    void duplicateEntityCandidatesFailClosed() {
        assertEquals(SupplyVillagerRecoveryDecision.Decision.DUPLICATE,
                SupplyVillagerRecoveryDecision.decide(
                        shipment, List.of(UUID.randomUUID(), UUID.randomUUID())));
    }
}
