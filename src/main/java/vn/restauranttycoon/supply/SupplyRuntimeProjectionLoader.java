package vn.restauranttycoon.supply;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

public final class SupplyRuntimeProjectionLoader {
    private final SupplyFulfillmentRepository repository;

    public SupplyRuntimeProjectionLoader(SupplyFulfillmentRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Optional<SupplyRuntimeProjection> load(SupplyRuntimeWork work) throws SQLException {
        Objects.requireNonNull(work, "work");
        Optional<SupplyRuntimeProjection> projection = repository.findRuntimeProjection(
                work.shipmentId(), work.restaurantId());
        if (projection.isEmpty()) return Optional.empty();
        SupplyRuntimeProjection value = projection.get();
        if (!value.packageId().equals(work.packageId())) return Optional.empty();
        return Optional.of(value);
    }
}
