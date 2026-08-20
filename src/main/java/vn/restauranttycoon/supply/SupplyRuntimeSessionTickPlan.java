package vn.restauranttycoon.supply;

public record SupplyRuntimeSessionTickPlan(
        Action action,
        boolean renewLease) {
    public enum Action {
        MOVE,
        TRANSITION,
        MANUAL,
        REMOVE
    }
}
