package vn.restauranttycoon.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.supply.IngredientCatalog;

public final class MarketMenuModel {
    private final IngredientCatalog catalog;
    private final List<MarketMenuEntry> entries;
    private final Map<String, Integer> quantities;

    private MarketMenuModel(
            IngredientCatalog catalog,
            List<MarketMenuEntry> entries,
            Map<String, Integer> quantities
    ) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.entries = List.copyOf(entries);
        this.quantities = Map.copyOf(quantities);
    }

    public static MarketMenuModel from(IngredientCatalog catalog, List<MarketPrice> prices) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(prices, "prices");
        var bySku = prices.stream().collect(Collectors.toMap(MarketPrice::sku, price -> price));
        List<MarketMenuEntry> entries = catalog.definitions().stream()
                .map(definition -> {
                    MarketPrice price = bySku.get(definition.sku());
                    if (price == null) {
                        throw new IllegalArgumentException("Missing market price: " + definition.sku());
                    }
                    return new MarketMenuEntry(definition, price);
                })
                .toList();
        return new MarketMenuModel(catalog, entries, Map.of());
    }

    public MarketMenuModel increase(String sku, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Increase amount must be positive");
        MarketMenuEntry entry = require(sku);
        int next = Math.addExact(quantity(sku), amount);
        if (next > entry.ingredient().maxQuantity()) {
            throw new IllegalArgumentException("Quantity exceeds ingredient limit");
        }
        return copyWithQuantity(sku, next);
    }

    public MarketMenuModel decrease(String sku, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Decrease amount must be positive");
        require(sku);
        int next = Math.max(0, quantity(sku) - amount);
        return copyWithQuantity(sku, next);
    }

    public int quantity(String sku) {
        require(sku);
        return quantities.getOrDefault(sku, 0);
    }

    public int selectedItemCount() {
        return quantities.values().stream().mapToInt(Integer::intValue).sum();
    }

    public CurrencyAmount total() {
        long total = 0;
        for (MarketMenuEntry entry : entries) {
            total = Math.addExact(total,
                    Math.multiplyExact(entry.price().unitPrice(), quantity(entry.ingredient().sku())));
        }
        return new CurrencyAmount(total);
    }

    public IngredientCatalog catalog() { return catalog; }
    public List<MarketMenuEntry> entries() { return entries; }

    public MarketMenuEntry require(String sku) {
        return entries.stream()
                .filter(entry -> entry.ingredient().sku().equals(sku))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown market SKU: " + sku));
    }

    private MarketMenuModel copyWithQuantity(String sku, int quantity) {
        Map<String, Integer> copy = new LinkedHashMap<>(quantities);
        if (quantity == 0) copy.remove(sku); else copy.put(sku, quantity);
        return new MarketMenuModel(catalog, entries, copy);
    }
}

final class MarketMenuState {
    private MarketMenuState() {}
}
