package vn.restauranttycoon.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class RestaurantMenuLayoutTest {
    @Test
    void assignsConfiguredRestaurantsToStableOrderSlots() {
        RestaurantMenuLayout layout = new RestaurantMenuLayout(List.of("plot_b", "plot_a"));

        assertEquals(Map.of("plot_a", 19, "plot_b", 20), layout.orderSlots());
        assertEquals(54, layout.inventorySize());
        assertTrue(layout.blankSlots().contains(0));
        assertFalse(layout.blankSlots().contains(19));
        assertFalse(layout.blankSlots().contains(RestaurantMenuLayout.BALANCE_SLOT));
    }

    @Test
    void orderSlotsNeverOverwriteGuideOrBalance() {
        RestaurantMenuLayout layout = new RestaurantMenuLayout(List.of(
                "p01", "p02", "p03", "p04", "p05", "p06", "p07", "p08", "p09", "p10", "p11",
                "p12", "p13", "p14", "p15", "p16", "p17", "p18", "p19", "p20", "p21"));

        assertFalse(layout.orderSlots().containsValue(RestaurantMenuLayout.GUIDE_SLOT));
        assertFalse(layout.orderSlots().containsValue(RestaurantMenuLayout.BALANCE_SLOT));
        assertEquals(21, layout.orderSlots().size());
    }

    @Test
    void rejectsMoreRestaurantsThanDashboardCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RestaurantMenuLayout(List.of(
                "p01", "p02", "p03", "p04", "p05", "p06", "p07", "p08", "p09", "p10", "p11",
                "p12", "p13", "p14", "p15", "p16", "p17", "p18", "p19", "p20", "p21", "p22")));
    }
}
