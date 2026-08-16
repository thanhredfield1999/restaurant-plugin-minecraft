package vn.restauranttycoon.economy;

import java.util.Objects;
import java.util.UUID;

public record OperationKey(UUID value) {
    public OperationKey {
        Objects.requireNonNull(value, "value");
    }

    public static OperationKey create() {
        return new OperationKey(UUID.randomUUID());
    }
}
