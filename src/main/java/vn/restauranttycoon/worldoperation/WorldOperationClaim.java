package vn.restauranttycoon.worldoperation;

import java.time.Instant;
import java.util.UUID;

public record WorldOperationClaim(
        UUID worldOperationId,
        UUID purchaseId,
        String plotId,
        long requiredFenceToken,
        long targetStageRevision,
        int attemptCount,
        String instanceId,
        UUID claimToken,
        Instant claimExpiresAt
) {
}
