package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public record SupplyRuntimeTransitionCommand(
        long expectedRevision,
        String expectedStage,
        int expectedIndex,
        String nextStage,
        int nextIndex,
        UUID operationId) {
    public SupplyRuntimeTransitionCommand {
        if (expectedRevision < 1) throw new IllegalArgumentException("expectedRevision must be positive");
        Objects.requireNonNull(expectedStage, "expectedStage");
        Objects.requireNonNull(nextStage, "nextStage");
        if (expectedIndex < 0 || nextIndex < 0) throw new IllegalArgumentException("checkpoint index must not be negative");
        Objects.requireNonNull(operationId, "operationId");
    }
}
