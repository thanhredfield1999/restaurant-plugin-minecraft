package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.Optional;

/** Load projection on claim callback thread, then marshal only Bukkit-facing work. */
public final class SupplyRuntimeClaimProjectionDispatcher {
    private final SupplyRuntimeProjectionSource source;
    private final SupplyRuntimeMainThreadExecutor mainThreadExecutor;
    private final SupplyRuntimeProjectionHandler handler;

    public SupplyRuntimeClaimProjectionDispatcher(
            SupplyRuntimeProjectionSource source,
            SupplyRuntimeMainThreadExecutor mainThreadExecutor,
            SupplyRuntimeProjectionHandler handler) {
        this.source = Objects.requireNonNull(source, "source");
        this.mainThreadExecutor = Objects.requireNonNull(mainThreadExecutor, "mainThreadExecutor");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    public void handle(SupplyRuntimeClaim claim) throws Exception {
        Objects.requireNonNull(claim, "claim");
        Optional<SupplyRuntimeProjection> projection = source.load(claim);
        if (projection.isEmpty()) return;
        mainThreadExecutor.execute(() -> handler.handle(claim, projection.get()));
    }
}
