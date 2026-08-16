package vn.restauranttycoon.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginSettingsTest {
    @Test
    void loadsWorkerAndPlotDefinitions() {
        PluginSettings settings = PluginSettings.from(config("""
                schema-version: 2
                database:
                  enabled: false
                  host: localhost
                  port: 5432
                  name: restaurant_tycoon
                  username: restaurant_tycoon
                  password: change-me
                  maximum-pool-size: 10
                  connection-timeout-ms: 5000
                gameplay:
                  max-active-plots: 20
                  max-physical-customers-per-plot: 4
                world-operations:
                  instance-id: paper-1
                  poll-ticks: 10
                  lease-seconds: 30
                  blocks-per-tick: 500
                plots:
                  plot_1:
                    world: world
                    origin: {x: -10, y: 64, z: 20}
                    trigger:
                      min: {x: -12, y: 64, z: 18}
                      max: {x: -8, y: 67, z: 22}
                drink-dispensers:
                  fill-seconds: 3
                  stations:
                    water_1:
                      world: world
                      location: {x: -8, y: 65, z: 20}
                      drink: WATER
                """));

        assertEquals("paper-1", settings.worldOperations().instanceId());
        assertEquals(500, settings.worldOperations().blocksPerTick());
        assertEquals(new PlotSettings(
                "plot_1", "world", -10, 64, 20,
                new PlotTriggerSettings(-12, 64, 18, -8, 67, 22)), settings.plots().get(0));
        assertEquals(3, settings.drinkDispensers().fillSeconds());
        assertEquals(
                new DrinkStationSettings("water_1", "world", -8, 65, 20, DrinkType.WATER),
                settings.drinkDispensers().stations().get(0));
    }

    @Test
    void rejectsMissingPlotsAndUnsafeWorkerLimits() {
        YamlConfiguration missingPlots = baseConfig();
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(missingPlots));

        YamlConfiguration invalidBatch = baseConfig();
        invalidBatch.set("plots.plot_1.world", "world");
        invalidBatch.set("plots.plot_1.origin.x", 0);
        invalidBatch.set("plots.plot_1.origin.y", 64);
        invalidBatch.set("plots.plot_1.origin.z", 0);
        invalidBatch.set("world-operations.blocks-per-tick", 0);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(invalidBatch));
    }

    @Test
    void rejectsMissingOrInvertedPlotTrigger() {
        YamlConfiguration missingTrigger = baseConfig();
        addPlotOrigin(missingTrigger);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(missingTrigger));

        YamlConfiguration invertedTrigger = baseConfig();
        addPlotOrigin(invertedTrigger);
        invertedTrigger.set("plots.plot_1.trigger.min.x", 2);
        invertedTrigger.set("plots.plot_1.trigger.min.y", 64);
        invertedTrigger.set("plots.plot_1.trigger.min.z", 2);
        invertedTrigger.set("plots.plot_1.trigger.max.x", 1);
        invertedTrigger.set("plots.plot_1.trigger.max.y", 67);
        invertedTrigger.set("plots.plot_1.trigger.max.z", 4);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(invertedTrigger));
    }

    @Test
    void rejectsUnsupportedDrinkAndDuplicateStationLocation() {
        YamlConfiguration unsupportedDrink = baseConfig();
        addPlot(unsupportedDrink);
        unsupportedDrink.set("drink-dispensers.fill-seconds", 3);
        unsupportedDrink.set("drink-dispensers.stations.coffee.world", "world");
        unsupportedDrink.set("drink-dispensers.stations.coffee.location.x", 1);
        unsupportedDrink.set("drink-dispensers.stations.coffee.location.y", 64);
        unsupportedDrink.set("drink-dispensers.stations.coffee.location.z", 2);
        unsupportedDrink.set("drink-dispensers.stations.coffee.drink", "COFFEE");
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(unsupportedDrink));

        YamlConfiguration duplicateLocation = baseConfig();
        addPlot(duplicateLocation);
        duplicateLocation.set("drink-dispensers.fill-seconds", 3);
        addWaterStation(duplicateLocation, "water_1", 1, 64, 2);
        addWaterStation(duplicateLocation, "water_2", 1, 64, 2);
        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(duplicateLocation));
    }

    @Test
    void rejectsPartiallyDeclaredDrinkDispenserSection() {
        YamlConfiguration config = baseConfig();
        addPlot(config);
        config.set("drink-dispensers.fill-seconds", 3);

        assertThrows(IllegalArgumentException.class, () -> PluginSettings.from(config));
    }

    private YamlConfiguration baseConfig() {
        return config("""
                schema-version: 2
                database:
                  enabled: false
                  host: localhost
                  port: 5432
                  name: restaurant_tycoon
                  username: restaurant_tycoon
                  password: change-me
                  maximum-pool-size: 10
                  connection-timeout-ms: 5000
                gameplay:
                  max-active-plots: 20
                  max-physical-customers-per-plot: 4
                world-operations:
                  instance-id: paper-1
                  poll-ticks: 10
                  lease-seconds: 30
                  blocks-per-tick: 500
                """);
    }

    private YamlConfiguration config(String yaml) {
        return YamlConfiguration.loadConfiguration(new java.io.StringReader(yaml));
    }

    private void addPlot(YamlConfiguration config) {
        addPlotOrigin(config);
        config.set("plots.plot_1.trigger.min.x", -2);
        config.set("plots.plot_1.trigger.min.y", 64);
        config.set("plots.plot_1.trigger.min.z", -2);
        config.set("plots.plot_1.trigger.max.x", 2);
        config.set("plots.plot_1.trigger.max.y", 67);
        config.set("plots.plot_1.trigger.max.z", 2);
    }

    private void addPlotOrigin(YamlConfiguration config) {
        config.set("plots.plot_1.world", "world");
        config.set("plots.plot_1.origin.x", 0);
        config.set("plots.plot_1.origin.y", 64);
        config.set("plots.plot_1.origin.z", 0);
    }

    private void addWaterStation(
            YamlConfiguration config,
            String stationId,
            int x,
            int y,
            int z
    ) {
        String path = "drink-dispensers.stations." + stationId;
        config.set(path + ".world", "world");
        config.set(path + ".location.x", x);
        config.set(path + ".location.y", y);
        config.set(path + ".location.z", z);
        config.set(path + ".drink", "WATER");
    }
}
