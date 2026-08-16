package vn.restauranttycoon.supply;

import java.util.UUID;

public final class OperationConflictException extends IllegalStateException {
    public OperationConflictException(UUID operationId) {
        super("Operation ID was already used for a different transition: " + operationId);
    }
}
