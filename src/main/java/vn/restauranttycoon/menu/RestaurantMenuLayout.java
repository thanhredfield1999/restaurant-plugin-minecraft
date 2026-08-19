package vn.restauranttycoon.menu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RestaurantMenuLayout {
    public static final int INVENTORY_SIZE = 54;
    public static final int BALANCE_SLOT = 4;
    public static final int GUIDE_SLOT = 31;
    public static final int FIRST_ORDER_SLOT = 19;
    public static final int MAX_RESTAURANTS = 21;

    private final Map<String, Integer> orderSlots;

    public RestaurantMenuLayout(List<String> restaurantIds) {
        Objects.requireNonNull(restaurantIds, "restaurantIds");
        List<String> sorted = restaurantIds.stream().sorted().toList();
        if (sorted.size() > MAX_RESTAURANTS) {
            throw new IllegalArgumentException("Restaurant dashboard supports at most " + MAX_RESTAURANTS + " restaurants");
        }
        Map<String, Integer> slots = new LinkedHashMap<>();
        int slot = FIRST_ORDER_SLOT;
        for (String restaurantId : sorted) {
            while (slot == BALANCE_SLOT || slot == GUIDE_SLOT) {
                slot++;
            }
            slots.put(restaurantId, slot++);
        }
        this.orderSlots = Collections.unmodifiableMap(slots);
    }

    public int inventorySize() {
        return INVENTORY_SIZE;
    }

    public Map<String, Integer> orderSlots() {
        return orderSlots;
    }

    public Set<Integer> blankSlots() {
        Set<Integer> result = new LinkedHashSet<>();
        Set<Integer> functional = new LinkedHashSet<>(orderSlots.values());
        functional.add(BALANCE_SLOT);
        functional.add(GUIDE_SLOT);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (!functional.contains(slot)) {
                result.add(slot);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
