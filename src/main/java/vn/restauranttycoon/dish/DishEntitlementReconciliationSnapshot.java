package vn.restauranttycoon.dish;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DishEntitlementReconciliationSnapshot(
        Map<UUID, DishEntitlement> projectionEntitlements,
        Map<UUID, DishEntitlement> tokenValidationEntitlements
) {
    public DishEntitlementReconciliationSnapshot {
        projectionEntitlements = Map.copyOf(Objects.requireNonNull(
                projectionEntitlements, "projectionEntitlements"));
        tokenValidationEntitlements = Map.copyOf(Objects.requireNonNull(
                tokenValidationEntitlements, "tokenValidationEntitlements"));
    }
}
