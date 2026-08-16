package vn.restauranttycoon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supply.BukkitIngredientCatalogLoader;
import vn.restauranttycoon.supply.IngredientCatalog;

class BundledConfigTest {
    @Test
    void bundledConfigParsesWithPluginAndCatalogLoaders() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                bundledConfig(), StandardCharsets.UTF_8));

        PluginSettings settings = PluginSettings.from(config);
        assertEquals(2, settings.schemaVersion());
        assertEquals(1, settings.plots().size());
        assertEquals("plot_1", settings.plots().get(0).plotId());

        IngredientCatalog catalog = new BukkitIngredientCatalogLoader().load(config);
        assertEquals(1, catalog.version());
        assertEquals(2, catalog.definitions().size());
        assertEquals("tomato", catalog.require("tomato").sku());
        assertEquals("rice", catalog.require("rice").sku());
    }

    private InputStream bundledConfig() {
        InputStream stream = getClass().getResourceAsStream("/config.yml");
        if (stream == null) {
            throw new IllegalStateException("Bundled config.yml is missing");
        }
        return stream;
    }
}
