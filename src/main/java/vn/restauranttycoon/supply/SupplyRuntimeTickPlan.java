package vn.restauranttycoon.supply;

import java.util.Objects;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

public record SupplyRuntimeTickPlan(SupplyRuntimeTickAction action, SupplySetupPosition target) {
    public SupplyRuntimeTickPlan {
        Objects.requireNonNull(action, "action");
        if (action == SupplyRuntimeTickAction.MARK_PENDING_MANUAL) {
            if (target != null) throw new IllegalArgumentException("manual recovery cannot have target");
        } else {
            Objects.requireNonNull(target, "target");
        }
    }
}
