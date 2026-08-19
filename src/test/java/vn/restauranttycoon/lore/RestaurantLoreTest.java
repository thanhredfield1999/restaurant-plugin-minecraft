package vn.restauranttycoon.lore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RestaurantLoreTest {
    @Test
    void guidesNewOwnerThroughManualRestaurantJourneyWithoutClaimingUnverifiedDeliveryRuntime() {
        String dashboard = String.join(" ", RestaurantLore.dashboardGuide());
        String plot = String.join(" ", RestaurantLore.plotGuide("plot_1", 0, 65, 0));

        assertTrue(dashboard.contains("Chợ đầu mối"));
        assertTrue(dashboard.contains("tự nhận kiện"));
        assertTrue(plot.contains("Hiệp hội Phố Bếp"));
        assertTrue(plot.contains("plot_1"));
        assertFalse(dashboard.toLowerCase().contains("npc đã giao"));
        assertFalse(dashboard.toLowerCase().contains("tự vào kho"));
    }
}
