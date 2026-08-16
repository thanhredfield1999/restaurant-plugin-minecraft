package vn.restauranttycoon.purchase;

public final class PlotAssignmentMismatchException extends IllegalStateException {
    public PlotAssignmentMismatchException(PurchaseRequest request) {
        super("Account does not own the requested plot fence: "
                + request.plotId() + "@" + request.plotFenceToken());
    }
}
