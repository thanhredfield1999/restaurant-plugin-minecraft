package vn.restauranttycoon.dish;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DishEntitlement(
        UUID entitlementId,
        UUID orderId,
        UUID restaurantId,
        String recipeId,
        long recipeVersion,
        DishEntitlementState state,
        Optional<UUID> holderId,
        long stateRevision
) {
    public DishEntitlement {
        Objects.requireNonNull(entitlementId, "entitlementId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(restaurantId, "restaurantId");
        if (recipeId == null || !recipeId.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Recipe ID must match [a-z0-9_]{1,64}");
        }
        if (recipeVersion < 1) {
            throw new IllegalArgumentException("Recipe version must be positive");
        }
        Objects.requireNonNull(state, "state");
        holderId = Objects.requireNonNull(holderId, "holderId");
        if ((state == DishEntitlementState.AVAILABLE) != holderId.isEmpty()) {
            throw new IllegalArgumentException("Only an available entitlement may have no holder");
        }
        if (stateRevision < 0) {
            throw new IllegalArgumentException("State revision cannot be negative");
        }
    }
}
