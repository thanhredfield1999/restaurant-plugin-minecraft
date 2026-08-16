package vn.restauranttycoon.purchase;

import java.util.UUID;
import vn.restauranttycoon.economy.CurrencyAmount;

public record PurchaseResult(
        UUID purchaseId,
        UUID worldOperationId,
        CurrencyAmount balance,
        long accountRevision,
        boolean duplicate
) {
}
