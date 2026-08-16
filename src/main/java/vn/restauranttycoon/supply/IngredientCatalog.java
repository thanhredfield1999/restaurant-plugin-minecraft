package vn.restauranttycoon.supply;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class IngredientCatalog {
    private final int version;
    private final Map<String, IngredientDefinition> definitions;

    public IngredientCatalog(int version, IngredientDefinition... definitions) {
        if (version <= 0) {
            throw new IllegalArgumentException("Catalog version must be positive");
        }
        Objects.requireNonNull(definitions, "definitions");
        List<IngredientDefinition> values = List.of(definitions);
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Catalog definitions cannot contain null");
        }
        try {
            this.definitions = values.stream().collect(Collectors.toUnmodifiableMap(
                    IngredientDefinition::sku, Function.identity()));
        } catch (IllegalStateException exception) {
            throw new IllegalArgumentException("Duplicate ingredient SKU", exception);
        }
        this.version = version;
    }

    public int version() {
        return version;
    }

    public IngredientDefinition require(String sku) {
        IngredientDefinition definition = definitions.get(sku);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown ingredient SKU: " + sku);
        }
        return definition;
    }

    public List<IngredientDefinition> definitions() {
        return definitions.values().stream()
                .sorted(java.util.Comparator.comparing(IngredientDefinition::sku))
                .toList();
    }
}
