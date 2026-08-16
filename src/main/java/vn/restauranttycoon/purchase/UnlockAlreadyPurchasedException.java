package vn.restauranttycoon.purchase;

public final class UnlockAlreadyPurchasedException extends IllegalStateException {
    public UnlockAlreadyPurchasedException(String unlockId) {
        super("Unlock is already purchased: " + unlockId);
    }
}
