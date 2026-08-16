package vn.restauranttycoon.supplysetup;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SupplyRouteMenuLayout {
    public static final int INVENTORY_SIZE = 27;
    private static final List<Integer> WAYPOINT_SLOTS = List.of(0, 1, 2, 3, 4, 5, 6);
    private static final int ADD_SLOT = 8;
    private static final int BACK_SLOT = 22;

    public List<Integer> waypointSlots() {
        return WAYPOINT_SLOTS;
    }

    public int addSlot() {
        return ADD_SLOT;
    }

    public int backSlot() {
        return BACK_SLOT;
    }

    public Set<Integer> blankSlots() {
        Set<Integer> blanks = new LinkedHashSet<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (!WAYPOINT_SLOTS.contains(slot) && slot != ADD_SLOT && slot != BACK_SLOT) {
                blanks.add(slot);
            }
        }
        return Collections.unmodifiableSet(blanks);
    }
}
