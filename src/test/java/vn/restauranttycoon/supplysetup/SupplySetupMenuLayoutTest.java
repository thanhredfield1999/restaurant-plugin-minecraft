package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class SupplySetupMenuLayoutTest {
    @Test
    void centralMenuUsesWhiteGlassForEveryNonFunctionalSlot() {
        SupplySetupMenuLayout layout = new SupplySetupMenuLayout();

        Set<Integer> blanks = layout.blankSlots(SupplySetupScope.CENTRAL_SUPPLIER);

        assertEquals(25, blanks.size());
        assertTrue(blanks.contains(0));
        assertFalse(blanks.contains(10));
        assertFalse(blanks.contains(16));
    }

    @Test
    void restaurantMenuUsesWhiteGlassAroundAllRestaurantPointSlots() {
        SupplySetupMenuLayout layout = new SupplySetupMenuLayout();

        Set<Integer> blanks = layout.blankSlots(SupplySetupScope.RESTAURANT);

        assertEquals(20, blanks.size());
        assertFalse(blanks.contains(10));
        assertFalse(blanks.contains(11));
        assertFalse(blanks.contains(12));
        assertFalse(blanks.contains(14));
        assertFalse(blanks.contains(15));
        assertFalse(blanks.contains(16));
        assertFalse(blanks.contains(SupplySetupMenuLayout.ROUTE_SLOT));
        assertTrue(layout.functionalSlots(SupplySetupScope.RESTAURANT)
                .contains(SupplySetupMenuLayout.ROUTE_SLOT));
        assertFalse(layout.functionalSlots(SupplySetupScope.CENTRAL_SUPPLIER)
                .contains(SupplySetupMenuLayout.ROUTE_SLOT));
    }
}
