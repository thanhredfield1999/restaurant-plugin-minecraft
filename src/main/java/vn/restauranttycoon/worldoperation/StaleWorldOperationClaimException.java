package vn.restauranttycoon.worldoperation;

public final class StaleWorldOperationClaimException extends IllegalStateException {
    public StaleWorldOperationClaimException(WorldOperationClaim claim) {
        super("World operation claim is stale or no longer owned: " + claim.worldOperationId());
    }
}
