package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

/** Executes pure tick decisions; DB transition remains caller-owned. */
public final class SupplyRuntimeTickExecutor {
    private final SupplyRuntimeEntityMover mover;
    private final SupplyRuntimeCheckpointTransitioner transitioner;

    public SupplyRuntimeTickExecutor(SupplyRuntimeEntityMover mover,
            SupplyRuntimeCheckpointTransitioner transitioner) {
        this.mover = Objects.requireNonNull(mover, "mover");
        this.transitioner = Objects.requireNonNull(transitioner, "transitioner");
    }

    public void execute(SupplyRuntimeProjection projection, List<UUID> candidates,
            SupplyVillagerMovementOutcome outcome) {
        SupplyRuntimeTickPlan plan = SupplyRuntimeTickPlanner.plan(projection, candidates, outcome);
        switch (plan.action()) {
            case MOVE -> mover.move(projection, plan.target());
            case TRANSITION_CHECKPOINT -> transitioner.transition(projection);
            default -> { }
        }
    }
}

@FunctionalInterface
interface SupplyRuntimeEntityMover {
    void move(SupplyRuntimeProjection projection, SupplySetupPosition target);
}

@FunctionalInterface
interface SupplyRuntimeCheckpointTransitioner {
    void transition(SupplyRuntimeProjection projection);
}
