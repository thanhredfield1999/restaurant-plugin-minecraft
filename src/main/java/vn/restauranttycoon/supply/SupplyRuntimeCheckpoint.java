package vn.restauranttycoon.supply;

import java.util.Objects;

public record SupplyRuntimeCheckpoint(String stage, int index) {
    public SupplyRuntimeCheckpoint {
        Objects.requireNonNull(stage, "stage");
        if (index < 0) throw new IllegalArgumentException("checkpoint index must not be negative");
    }
}
