package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SupplySetupPointTest {
    @Test
    void acceptsRestaurantDeliveryPointWithExactTeleportPose() {
        SupplySetupPoint point = new SupplySetupPoint(
                SupplySetupOwner.restaurant("plot_1"),
                SupplySetupPointType.DELIVERY_STOP,
                new SupplySetupPosition("world", 10.25, 64.0, -4.75, 90.0f, 15.0f));

        assertEquals("plot_1", point.owner().ownerId());
        assertEquals(90.0f, point.position().yaw());
        assertEquals(15.0f, point.position().pitch());
    }

    @Test
    void rejectsPointTypeOwnedByWrongScope() {
        assertThrows(IllegalArgumentException.class, () -> new SupplySetupPoint(
                SupplySetupOwner.restaurant("plot_1"),
                SupplySetupPointType.ORDER_DESK,
                new SupplySetupPosition("world", 0, 64, 0, 0, 0)));
    }

    @Test
    void rejectsUnsafeIdentifiersAndNonFinitePose() {
        assertThrows(IllegalArgumentException.class,
                () -> SupplySetupOwner.restaurant("plot id"));
        assertThrows(IllegalArgumentException.class,
                () -> new SupplySetupPosition("world", Double.NaN, 64, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SupplySetupPosition("world", 0, 64, 0, Float.POSITIVE_INFINITY, 0));
    }
}
