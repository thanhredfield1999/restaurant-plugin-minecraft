package vn.restauranttycoon.supply;

@FunctionalInterface
public interface SupplyRuntimeClaimDispatchHandler {
    void handle(SupplyRuntimeClaim claim);
}
