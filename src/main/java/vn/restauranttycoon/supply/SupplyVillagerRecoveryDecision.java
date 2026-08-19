package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SupplyVillagerRecoveryDecision {
    private SupplyVillagerRecoveryDecision() {
    }

    public static Decision decide(UUID shipmentId, List<UUID> entityCandidates) {
        Objects.requireNonNull(shipmentId, "shipmentId");
        Objects.requireNonNull(entityCandidates, "entityCandidates");
        entityCandidates.forEach(candidate -> Objects.requireNonNull(candidate, "entityCandidate"));
        if (entityCandidates.size() > 1) return Decision.DUPLICATE;
        return entityCandidates.isEmpty() ? Decision.SPAWN : Decision.REUSE;
    }

    public enum Decision {
        REUSE,
        SPAWN,
        DUPLICATE
    }
}
