package vn.restauranttycoon.economy;

public final class OperationKeyConflictException extends IllegalStateException {
    public OperationKeyConflictException(OperationKey operationKey) {
        super("Operation key was reused with different immutable inputs: " + operationKey.value());
    }
}
