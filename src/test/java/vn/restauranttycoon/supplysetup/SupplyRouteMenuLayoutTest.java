package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SupplyRouteMenuLayoutTest {
    @Test
    void routeMenuReservesWaypointAddAndBackSlots() {
        SupplyRouteMenuLayout layout = new SupplyRouteMenuLayout();

        assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), layout.waypointSlots());
        assertEquals(8, layout.addSlot());
        assertEquals(22, layout.backSlot());
        assertTrue(layout.waypointSlots().contains(0));
        assertTrue(layout.waypointSlots().contains(6));
        assertFalse(layout.blankSlots().contains(8));
        assertFalse(layout.blankSlots().contains(22));
        assertEquals(18, layout.blankSlots().size());
    }
}

// Contract is intentionally written before the GUI implementation.
