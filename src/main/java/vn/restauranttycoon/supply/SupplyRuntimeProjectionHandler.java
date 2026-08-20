package vn.restauranttycoon.supply;

@FunctionalInterface
public interface SupplyRuntimeProjectionHandler {
    void handle(SupplyRuntimeClaim claim, SupplyRuntimeProjection projection);
}
