package vn.restauranttycoon.supply;

import java.util.Objects;
import vn.restauranttycoon.economy.CurrencyAmount;

public record SupplierOrderLine(
        String sku,
        String displayName,
        IngredientUnit unit,
        int quantity,
        CurrencyAmount unitPrice
) {
    public SupplierOrderLine {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public CurrencyAmount subtotal() {
        return new CurrencyAmount(Math.multiplyExact(unitPrice.units(), quantity));
    }
}
