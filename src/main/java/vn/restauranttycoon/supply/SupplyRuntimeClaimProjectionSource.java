package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.Optional;

/** Claim-to-work adapter. Projection I/O remains delegated to supplied loader. */
public final class SupplyRuntimeClaimProjectionSource {
    private final SupplyRuntimeWorkLoader loader;

    public SupplyRuntimeClaimProjectionSource(SupplyRuntimeWorkLoader loader) {
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public Optional<SupplyRuntimeProjection> load(SupplyRuntimeClaim claim) throws Exception {
        Objects.requireNonNull(claim, "claim");
        SupplyRuntimeWork work = new SupplyRuntimeWork(
                claim.shipmentId(), claim.packageId(), claim.restaurantId(),
                claim.shipmentState(), claim.packageState());
        return loader.load(work);
    }
}

@FunctionalInterface
interface SupplyRuntimeWorkLoader {
    Optional<SupplyRuntimeProjection> load(SupplyRuntimeWork work) throws Exception;
}
