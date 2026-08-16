package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SupplyRouteTest {
    @Test
    void acceptsOneBasedContiguousWaypointOrder() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");

        SupplyRoute route = new SupplyRoute(List.of(
                waypoint(owner, 1, "Cổng vào"),
                waypoint(owner, 2, "Điểm nhận hàng"),
                waypoint(owner, 3, "Cổng ra")));

        assertEquals(List.of(1, 2, 3), route.waypoints().stream()
                .map(SupplyRouteWaypoint::sequence).toList());
    }

    @Test
    void rejectsMissingSequence() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyRoute(List.of(
                        waypoint(owner, 1, "Cổng vào"),
                        waypoint(owner, 3, "Cổng ra"))));

        assertEquals("waypoint sequence must be contiguous from 1", error.getMessage());
    }

    @Test
    void rejectsWaypointFromAnotherRestaurant() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyRoute(List.of(
                        waypoint(SupplySetupOwner.restaurant("plot_1"), 1, "Cổng vào"),
                        waypoint(SupplySetupOwner.restaurant("plot_2"), 2, "Cổng ra"))));

        assertEquals("all waypoints must belong to the same restaurant", error.getMessage());
    }

    @Test
    void rejectsMoreThanSevenWaypoints() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyRoute(owner, java.util.stream.IntStream.rangeClosed(1, 8)
                        .mapToObj(sequence -> waypoint(owner, sequence, "Điểm " + sequence))
                        .toList()));

        assertEquals("route cannot contain more than 7 waypoints", error.getMessage());
    }

    private static SupplyRouteWaypoint waypoint(
            SupplySetupOwner owner,
            int sequence,
            String name
    ) {
        return new SupplyRouteWaypoint(owner, sequence, name, new SupplySetupPosition(
                "world", sequence, 64, sequence, 0, 0));
    }
}

final class SupplyRouteWaypointTest {
    @Test
    void rejectsCentralSupplierOwner() {
        assertThrows(IllegalArgumentException.class, () -> new SupplyRouteWaypoint(
                SupplySetupOwner.centralSupplier(),
                1,
                "Không hợp lệ",
                new SupplySetupPosition("world", 0, 64, 0, 0, 0)));
    }

    @Test
    void rejectsBlankNameAndNonPositiveSequence() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        assertThrows(IllegalArgumentException.class, () -> new SupplyRouteWaypoint(
                owner, 0, "Tên", new SupplySetupPosition("world", 0, 64, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new SupplyRouteWaypoint(
                owner, 1, " ", new SupplySetupPosition("world", 0, 64, 0, 0, 0)));
    }
}

// Route tests intentionally define the desired public contract before implementation.
// The production types are added in the following RED-GREEN step.
