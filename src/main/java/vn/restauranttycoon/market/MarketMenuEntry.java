package vn.restauranttycoon.market;

import java.util.Objects;
import vn.restauranttycoon.supply.IngredientDefinition;

public record MarketMenuEntry(
        IngredientDefinition ingredient,
        MarketPrice price
) {
    public MarketMenuEntry {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(price, "price");
        if (!ingredient.sku().equals(price.sku())) {
            throw new IllegalArgumentException("Ingredient and market SKU must match");
        }
    }

    public long priceDeltaFromBase() {
        return price.unitPrice() - price.basePrice();
    }

    public String trend() {
        long delta = priceDeltaFromBase();
        return delta == 0 ? "Ổn định" : delta > 0 ? "Tăng" : "Giảm";
    }
}
