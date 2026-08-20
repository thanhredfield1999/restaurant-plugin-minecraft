package vn.restauranttycoon.supply;

public final class SupplyRuntimeSessionTickPlanner {
    private SupplyRuntimeSessionTickPlanner() {
    }

    public static SupplyRuntimeSessionTickPlan plan(
            SupplyRuntimeSessionTickContext context,
            boolean leaseDue) {
        return switch (context) {
            case MOVING -> new SupplyRuntimeSessionTickPlan(SupplyRuntimeSessionTickPlan.Action.MOVE, leaseDue);
            case ARRIVED -> new SupplyRuntimeSessionTickPlan(SupplyRuntimeSessionTickPlan.Action.TRANSITION, leaseDue);
            case STUCK, MISSING -> new SupplyRuntimeSessionTickPlan(SupplyRuntimeSessionTickPlan.Action.MANUAL, leaseDue);
        };
    }
}
