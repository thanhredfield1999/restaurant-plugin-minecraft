package vn.restauranttycoon.supply;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import vn.restauranttycoon.economy.CurrencyAmount;

public final class SupplyOrderMenuModel {
    private final IngredientCatalog catalog;
    private final Map<String, Integer> quantities;

    private SupplyOrderMenuModel(IngredientCatalog catalog, Map<String, Integer> quantities) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.quantities = Map.copyOf(quantities);
    }

    public static SupplyOrderMenuModel empty(IngredientCatalog catalog) {
        return new SupplyOrderMenuModel(catalog, Map.of());
    }

    public SupplyOrderMenuModel increase(String sku, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Increase amount must be positive");
        IngredientDefinition definition = catalog.require(sku);
        int next = Math.addExact(quantity(sku), amount);
        if (next > definition.maxQuantity()) {
            throw new IllegalArgumentException("Quantity exceeds ingredient limit");
        }
        Map<String, Integer> copy = new LinkedHashMap<>(quantities);
        copy.put(sku, next);
        return new SupplyOrderMenuModel(catalog, copy);
    }

    public SupplyOrderMenuModel decrease(String sku, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Decrease amount must be positive");
        catalog.require(sku);
        int next = Math.max(0, quantity(sku) - amount);
        Map<String, Integer> copy = new LinkedHashMap<>(quantities);
        if (next == 0) copy.remove(sku); else copy.put(sku, next);
        return new SupplyOrderMenuModel(catalog, copy);
    }

    public int quantity(String sku) {
        catalog.require(sku);
        return quantities.getOrDefault(sku, 0);
    }

    public List<SupplyOrderMenuEntry> entries() {
        return catalog.definitions().stream()
                .map(definition -> new SupplyOrderMenuEntry(
                        definition.sku(), definition.displayName(), definition.unit(),
                        definition.unitPrice(), definition.maxQuantity(), quantity(definition.sku())))
                .toList();
    }

    public List<SupplyOrderMenuEntry> selectedLines() {
        List<SupplyOrderMenuEntry> selected = entries().stream()
                .filter(entry -> entry.quantity() > 0)
                .toList();
        if (selected.isEmpty()) throw new IllegalStateException("Cannot submit an empty order");
        return selected;
    }

    public CurrencyAmount total() {
        long total = 0;
        for (SupplyOrderMenuEntry entry : entries()) {
            total = Math.addExact(total, Math.multiplyExact(entry.unitPrice().units(), entry.quantity()));
        }
        return new CurrencyAmount(total);
    }
}
