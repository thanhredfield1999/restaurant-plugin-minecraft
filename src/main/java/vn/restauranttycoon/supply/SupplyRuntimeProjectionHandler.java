package vn.restauranttycoon.supply;

@FunctionalInterface
public interface SupplyRuntimeProjectionHandler {
    void handle(SupplyRuntimeProjection projection);
}
