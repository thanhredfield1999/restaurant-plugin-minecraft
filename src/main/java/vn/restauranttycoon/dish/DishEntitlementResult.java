package vn.restauranttycoon.dish;

import java.util.Objects;

public record DishEntitlementResult(DishEntitlement entitlement, boolean duplicate) {
    public DishEntitlementResult {
        Objects.requireNonNull(entitlement, "entitlement");
    }
}
