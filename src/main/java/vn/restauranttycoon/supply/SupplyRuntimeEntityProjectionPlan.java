package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public record SupplyRuntimeEntityProjectionPlan(Action action, UUID entityId) {
    public SupplyRuntimeEntityProjectionPlan {
        Objects.requireNonNull(action, "action");
        if (action == Action.REUSE) {
            Objects.requireNonNull(entityId, "entityId");
        } else if (entityId != null) {
            throw new IllegalArgumentException("Only REUSE plan may contain entityId");
        }
    }

    public enum Action {
        SPAWN,
        REUSE,
        PENDING_MANUAL
    }
}
