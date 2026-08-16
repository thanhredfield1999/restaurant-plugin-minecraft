package vn.restauranttycoon.dish;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record DishInventoryReconciliationPlan(
        List<DishInventorySlot> expectedSlots,
        Set<Integer> slotsToClear,
        List<DishInventoryGrant> grants
) {
    public DishInventoryReconciliationPlan {
        expectedSlots = List.copyOf(Objects.requireNonNull(expectedSlots, "expectedSlots"));
        slotsToClear = Set.copyOf(Objects.requireNonNull(slotsToClear, "slotsToClear"));
        grants = List.copyOf(Objects.requireNonNull(grants, "grants"));
    }

    /**
     * Checks semantic applicability, not byte-for-byte ItemStack identity. VANILLA
     * slots are never mutated, while every INVALID_TOKEN slot is cleared regardless
     * of malformed payload or amount. EMPTY transitions and valid token identity
     * changes remain observable in this snapshot.
     */
    public boolean isApplicableTo(List<DishInventorySlot> currentSlots) {
        return expectedSlots.equals(Objects.requireNonNull(currentSlots, "currentSlots"));
    }
}
