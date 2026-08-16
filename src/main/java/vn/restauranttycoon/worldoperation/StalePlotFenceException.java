package vn.restauranttycoon.worldoperation;

public final class StalePlotFenceException extends IllegalStateException {
    public StalePlotFenceException(String message) {
        super(message);
    }

    public StalePlotFenceException(WorldOperationClaim claim) {
        super("Plot assignment fence changed for world operation: " + claim.worldOperationId());
    }
}
