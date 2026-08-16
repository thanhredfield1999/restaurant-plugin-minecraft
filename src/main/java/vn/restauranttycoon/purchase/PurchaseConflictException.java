package vn.restauranttycoon.purchase;

public final class PurchaseConflictException extends IllegalStateException {
    public PurchaseConflictException(PurchaseRequest request) {
        super("Purchase operation was reused with different immutable inputs: "
                + request.operationKey().value());
    }
}
