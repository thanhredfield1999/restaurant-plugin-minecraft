package vn.restauranttycoon.supply;

import java.util.Optional;

@FunctionalInterface
public interface SupplyRuntimeProjectionSource {
    Optional<SupplyRuntimeProjection> load(SupplyRuntimeClaim claim) throws Exception;
}
