package vn.restauranttycoon.supply;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public final class BukkitIngredientCatalogLoader {
    public IngredientCatalog load(ConfigurationSection config) {
        ConfigurationSection section = config.getConfigurationSection("supply-catalog");
        if (section == null) throw new IllegalArgumentException("supply-catalog must be configured");
        int version = section.getInt("version");
        ConfigurationSection ingredients = section.getConfigurationSection("ingredients");
        if (ingredients == null || ingredients.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("supply-catalog.ingredients must not be empty");
        }
        List<IngredientDefinition> definitions = new ArrayList<>();
        for (String sku : ingredients.getKeys(false)) {
            ConfigurationSection ingredient = ingredients.getConfigurationSection(sku);
            if (ingredient == null) throw new IllegalArgumentException("Invalid ingredient: " + sku);
            definitions.add(new IngredientDefinition(
                    sku,
                    required(ingredient, "display-name"),
                    IngredientUnit.valueOf(required(ingredient, "unit").toUpperCase(java.util.Locale.ROOT)),
                    requiredLong(ingredient, "unit-price"),
                    requiredInt(ingredient, "max-quantity")));
        }
        if (definitions.size() > 7) {
            throw new IllegalArgumentException("supply-catalog supports at most 7 ingredients in the current GUI");
        }
        return new IngredientCatalog(version, definitions.toArray(IngredientDefinition[]::new));
    }

    private static String required(ConfigurationSection section, String key) {
        String value = section.getString(key, "").trim();
        if (value.isEmpty()) throw new IllegalArgumentException(key + " must not be blank");
        return value;
    }

    private static int requiredInt(ConfigurationSection section, String key) {
        if (!section.isInt(key)) throw new IllegalArgumentException(key + " must be an integer");
        return section.getInt(key);
    }

    private static long requiredLong(ConfigurationSection section, String key) {
        if (!section.isLong(key) && !section.isInt(key)) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return section.getLong(key);
    }
}