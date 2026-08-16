package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SupplySetupReadinessEvaluatorTest {
    private final SupplySetupReadinessEvaluator evaluator = new SupplySetupReadinessEvaluator();

    @Test
    void reportsCentralSupplierStructurallyComplete() {
        SupplySetupOwner owner = SupplySetupOwner.centralSupplier();

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                List.of(
                        point(owner, SupplySetupPointType.ORDER_DESK, "world", 1),
                        point(owner, SupplySetupPointType.SUPPLIER_SPAWN, "world", 2)),
                null);

        assertTrue(readiness.structurallyComplete());
        assertEquals(List.of(), readiness.issues());
    }

    @Test
    void reportsEveryMissingRestaurantPointInSetupOrder() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                List.of(
                        point(owner, SupplySetupPointType.DELIVERY_ENTRY, "world", 1),
                        point(owner, SupplySetupPointType.DELIVERY_STOP, "world", 2)),
                new SupplyRoute(owner, List.of()));

        assertFalse(readiness.structurallyComplete());
        assertEquals(List.of(
                SupplySetupReadinessIssue.missing(SupplySetupPointType.UNLOAD_POINT),
                SupplySetupReadinessIssue.missing(SupplySetupPointType.WAREHOUSE_ENTRY),
                SupplySetupReadinessIssue.missing(SupplySetupPointType.DELIVERY_EXIT),
                SupplySetupReadinessIssue.missing(SupplySetupPointType.DELIVERY_DESPAWN)),
                readiness.issues());
    }

    @Test
    void includesWarehouseEntryWhenRestaurantIsComplete() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                restaurantPoints(owner, "world"),
                new SupplyRoute(owner, List.of()));

        assertTrue(readiness.structurallyComplete());
    }

    @Test
    void rejectsPointOwnedByAnotherSetup() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        List<SupplySetupPoint> points = new java.util.ArrayList<>(restaurantPoints(owner, "world"));
        points.set(0, point(
                SupplySetupOwner.restaurant("plot_2"),
                SupplySetupPointType.DELIVERY_ENTRY,
                "world",
                1));

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                points,
                new SupplyRoute(owner, List.of()));

        assertFalse(readiness.structurallyComplete());
        assertEquals(List.of(SupplySetupReadinessIssue.foreignOwner()), readiness.issues());
    }

    @Test
    void rejectsSetupAcrossMultipleWorlds() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        List<SupplySetupPoint> points = new java.util.ArrayList<>(restaurantPoints(owner, "world"));
        points.set(5, point(owner, SupplySetupPointType.DELIVERY_DESPAWN, "another_world", 6));

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                points,
                new SupplyRoute(owner, List.of()));

        assertFalse(readiness.structurallyComplete());
        assertEquals(List.of(SupplySetupReadinessIssue.multipleWorlds()), readiness.issues());
    }

    @Test
    void identifiesDuplicatedPointType() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        List<SupplySetupPoint> points = new java.util.ArrayList<>(restaurantPoints(owner, "world"));
        points.add(point(owner, SupplySetupPointType.UNLOAD_POINT, "world", 7));

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                points,
                new SupplyRoute(owner, List.of()));

        assertEquals(
                List.of(SupplySetupReadinessIssue.duplicate(SupplySetupPointType.UNLOAD_POINT)),
                readiness.issues());
    }

    @Test
    void includesRouteWaypointsWhenCheckingJourneyWorld() {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplyRoute route = new SupplyRoute(owner, List.of(new SupplyRouteWaypoint(
                owner,
                1,
                "Khúc cua",
                new SupplySetupPosition("another_world", 10, 64, 0, 0, 0))));

        SupplySetupReadiness readiness = evaluator.assess(
                owner,
                restaurantPoints(owner, "world"),
                route);

        assertEquals(List.of(SupplySetupReadinessIssue.multipleWorlds()), readiness.issues());
    }

    private static List<SupplySetupPoint> restaurantPoints(
            SupplySetupOwner owner,
            String worldName
    ) {
        return List.of(
                point(owner, SupplySetupPointType.DELIVERY_ENTRY, worldName, 1),
                point(owner, SupplySetupPointType.DELIVERY_STOP, worldName, 2),
                point(owner, SupplySetupPointType.UNLOAD_POINT, worldName, 3),
                point(owner, SupplySetupPointType.WAREHOUSE_ENTRY, worldName, 4),
                point(owner, SupplySetupPointType.DELIVERY_EXIT, worldName, 5),
                point(owner, SupplySetupPointType.DELIVERY_DESPAWN, worldName, 6));
    }

    private static SupplySetupPoint point(
            SupplySetupOwner owner,
            SupplySetupPointType type,
            String worldName,
            double x
    ) {
        return new SupplySetupPoint(
                owner,
                type,
                new SupplySetupPosition(worldName, x, 64, 0, 0, 0));
    }
}
