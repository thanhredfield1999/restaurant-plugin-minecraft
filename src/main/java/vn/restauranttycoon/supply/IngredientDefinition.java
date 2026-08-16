package vn.restauranttycoon.supply;

import java.util.Objects;
import vn.restauranttycoon.economy.CurrencyAmount;

public record IngredientDefinition(
        String sku,
        String displayName,
        IngredientUnit unit,
        CurrencyAmount unitPrice,
        int maxQuantity
) {
    public IngredientDefinition(
            String sku,
            String displayName,
            IngredientUnit unit,
            long unitPrice,
            int maxQuantity
    ) {
        this(sku, displayName, unit, new CurrencyAmount(unitPrice), maxQuantity);
    }

    public IngredientDefinition {
        if (sku == null || !sku.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("SKU must match [a-z0-9_]{1,64}");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name is required");
        }
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (unitPrice.units() <= 0) {
            throw new IllegalArgumentException("Unit price must be positive");
        }
        if (maxQuantity <= 0) {
            throw new IllegalArgumentException("Maximum quantity must be positive");
        }
    }
}
