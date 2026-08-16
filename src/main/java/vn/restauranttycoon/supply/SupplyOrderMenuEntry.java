package vn.restauranttycoon.supply;

import vn.restauranttycoon.economy.CurrencyAmount;

public record SupplyOrderMenuEntry(
        String sku,
        String displayName,
        IngredientUnit unit,
        CurrencyAmount unitPrice,
        int maxQuantity,
        int quantity
) {
}
