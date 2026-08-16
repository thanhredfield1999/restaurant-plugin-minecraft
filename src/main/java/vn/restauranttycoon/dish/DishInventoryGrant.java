package vn.restauranttycoon.dish;

import java.util.Objects;

public record DishInventoryGrant(int slot, DishItemToken token) {
    public DishInventoryGrant {
        if (slot < 0) {
            throw new IllegalArgumentException("Slot cannot be negative");
        }
        Objects.requireNonNull(token, "token");
    }
}
