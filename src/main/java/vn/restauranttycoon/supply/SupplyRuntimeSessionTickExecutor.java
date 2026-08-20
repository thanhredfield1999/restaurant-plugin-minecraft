package vn.restauranttycoon.supply;

import java.util.Objects;

public final class SupplyRuntimeSessionTickExecutor {
    private SupplyRuntimeSessionTickExecutor() {
    }

    public static void execute(
            SupplyRuntimeSessionTickContext context,
            boolean leaseDue,
            Runnable move,
            Runnable renew,
            Runnable transition,
            Runnable manual) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(move, "move");
        Objects.requireNonNull(renew, "renew");
        Objects.requireNonNull(transition, "transition");
        Objects.requireNonNull(manual, "manual");
        SupplyRuntimeSessionTickPlan plan = SupplyRuntimeSessionTickPlanner.plan(context, leaseDue);
        if (plan.renewLease()) {
            renew.run();
        }
        switch (plan.action()) {
            case MOVE -> move.run();
            case TRANSITION -> transition.run();
            case MANUAL -> manual.run();
            case REMOVE -> { }
        }
    }
}
