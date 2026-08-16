package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SupplyDeliveryJourneyPlannerTest {
    @Test
    void buildsInboundUnloadAndExitJourneyInConfiguredOrder() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplyRoute route = new SupplyRoute(owner, List.of(
                waypoint(owner, 1, "Khúc cua"),
                waypoint(owner, 2, "Cổng phụ")));

        SupplyDeliveryJourneyPlan plan = new SupplyDeliveryJourneyPlanner().plan(
                owner,
                List.of(
                        point(owner, SupplySetupPointType.DELIVERY_DESPAWN, 60),
                        point(owner, SupplySetupPointType.UNLOAD_POINT, 40),
                        point(owner, SupplySetupPointType.DELIVERY_ENTRY, 10),
                        point(owner, SupplySetupPointType.WAREHOUSE_ENTRY, 45),
                        point(owner, SupplySetupPointType.DELIVERY_EXIT, 50),
                        point(owner, SupplySetupPointType.DELIVERY_STOP, 30)),
                route);

        assertEquals(List.of(
                SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                SupplyDeliveryJourneyStage.ROUTE_WAYPOINT,
                SupplyDeliveryJourneyStage.ROUTE_WAYPOINT,
                SupplyDeliveryJourneyStage.DELIVERY_STOP,
                SupplyDeliveryJourneyStage.UNLOAD_POINT,
                SupplyDeliveryJourneyStage.DELIVERY_EXIT,
                SupplyDeliveryJourneyStage.DELIVERY_DESPAWN), plan.steps().stream()
                .map(SupplyDeliveryJourneyStep::stage)
                .toList());
        assertEquals(List.of(10.0, 1.0, 2.0, 30.0, 40.0, 50.0, 60.0), plan.steps().stream()
                .map(step -> step.position().x())
                .toList());
    }

    @Test
    void rejectsRouteOwnedByAnotherRestaurant() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplySetupOwner anotherOwner = SupplySetupOwner.restaurant("plot_2");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyDeliveryJourneyPlanner().plan(
                        owner,
                        requiredPoints(owner),
                        new SupplyRoute(anotherOwner, List.of())));

        assertEquals("route must belong to journey owner", error.getMessage());
    }

    @Test
    void rejectsSetupPointOwnedByAnotherRestaurant() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplySetupOwner anotherOwner = SupplySetupOwner.restaurant("plot_2");
        List<SupplySetupPoint> points = new java.util.ArrayList<>(requiredPoints(owner));
        points.set(0, point(anotherOwner, SupplySetupPointType.DELIVERY_ENTRY, 10));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyDeliveryJourneyPlanner().plan(
                        owner,
                        points,
                        new SupplyRoute(owner, List.of())));

        assertEquals("all setup points must belong to journey owner", error.getMessage());
    }

    @Test
    void rejectsDuplicateSetupPointType() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        List<SupplySetupPoint> points = new java.util.ArrayList<>(requiredPoints(owner));
        points.add(point(owner, SupplySetupPointType.DELIVERY_ENTRY, 11));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyDeliveryJourneyPlanner().plan(
                        owner,
                        points,
                        new SupplyRoute(owner, List.of())));

        assertEquals("duplicate setup point: DELIVERY_ENTRY", error.getMessage());
    }

    @Test
    void rejectsJourneyAcrossMultipleWorlds() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        List<SupplySetupPoint> points = new java.util.ArrayList<>(requiredPoints(owner));
        points.set(4, new SupplySetupPoint(
                owner,
                SupplySetupPointType.DELIVERY_DESPAWN,
                new SupplySetupPosition("another_world", 60, 64, 0, 0, 0)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyDeliveryJourneyPlanner().plan(
                        owner,
                        points,
                        new SupplyRoute(owner, List.of())));

        assertEquals("delivery journey cannot cross worlds", error.getMessage());
    }

    @Test
    void rejectsPublicJourneyPlanForCentralSupplier() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                new SupplyDeliveryJourneyPlan(
                        SupplySetupOwner.centralSupplier(),
                        List.of(new SupplyDeliveryJourneyStep(
                                SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                                new SupplySetupPosition("world", 10, 64, 0, 0, 0)))));

        assertEquals("delivery journey must belong to a restaurant", error.getMessage());
    }

    private static List<SupplySetupPoint> requiredPoints(SupplySetupOwner owner) {
        return List.of(
                point(owner, SupplySetupPointType.DELIVERY_ENTRY, 10),
                point(owner, SupplySetupPointType.DELIVERY_STOP, 30),
                point(owner, SupplySetupPointType.UNLOAD_POINT, 40),
                point(owner, SupplySetupPointType.DELIVERY_EXIT, 50),
                point(owner, SupplySetupPointType.DELIVERY_DESPAWN, 60));
    }

    private static SupplySetupPoint point(
            SupplySetupOwner owner,
            SupplySetupPointType type,
            double x
    ) {
        return new SupplySetupPoint(owner, type,
                new SupplySetupPosition("world", x, 64, 0, 0, 0));
    }

    private static SupplyRouteWaypoint waypoint(
            SupplySetupOwner owner,
            int sequence,
            String name
    ) {
        return new SupplyRouteWaypoint(owner, sequence, name,
                new SupplySetupPosition("world", sequence, 64, 0, 0, 0));
    }
}
