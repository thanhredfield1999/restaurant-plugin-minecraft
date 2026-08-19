package vn.restauranttycoon.supply;

public final class StaleSupplyRuntimeClaimException extends IllegalStateException {
    public StaleSupplyRuntimeClaimException(SupplyRuntimeClaim claim) {
        super("Supply runtime claim is stale for shipment " + claim.shipmentId());
    }
}
