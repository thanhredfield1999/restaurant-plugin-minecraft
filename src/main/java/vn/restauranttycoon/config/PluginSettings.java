package vn.restauranttycoon.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public record PluginSettings(
        int schemaVersion,
        DatabaseSettings database,
        int maxActivePlots,
        int maxPhysicalCustomersPerPlot,
        WorldOperationSettings worldOperations,
        SupplyRuntimeSettings supplyRuntime,
        List<PlotSettings> plots,
        DrinkDispenserSettings drinkDispensers
) {
    public PluginSettings {
        plots = List.copyOf(plots);
    }

    public static PluginSettings from(FileConfiguration config) {
        int schemaVersion = config.getInt("schema-version");
        int maxActivePlots = config.getInt("gameplay.max-active-plots");
        int maxCustomers = config.getInt("gameplay.max-physical-customers-per-plot");

        if (schemaVersion != 2) {
            throw new IllegalArgumentException("Unsupported config schema-version: " + schemaVersion);
        }
        if (maxActivePlots < 1) {
            throw new IllegalArgumentException("gameplay.max-active-plots must be positive");
        }
        if (maxCustomers < 1 || maxCustomers > 20) {
            throw new IllegalArgumentException(
                    "gameplay.max-physical-customers-per-plot must be between 1 and 20");
        }

        DatabaseSettings database = DatabaseSettings.from(config);
        WorldOperationSettings worldOperations = new WorldOperationSettings(
                required(config, "world-operations.instance-id"),
                config.getInt("world-operations.poll-ticks"),
                config.getInt("world-operations.lease-seconds"),
                config.getInt("world-operations.blocks-per-tick"));
        SupplyRuntimeSettings supplyRuntime = SupplyRuntimeSettings.from(config);
        ConfigurationSection plotSection = config.getConfigurationSection("plots");
        if (plotSection == null || plotSection.getKeys(false).isEmpty()) {
            throw new IllegalArgumentException("plots must define at least one authored plot");
        }
        List<PlotSettings> plots = new ArrayList<>();
        for (String plotId : plotSection.getKeys(false)) {
            ConfigurationSection plot = plotSection.getConfigurationSection(plotId);
            if (plot == null) {
                throw new IllegalArgumentException("Invalid plot section: " + plotId);
            }
            plots.add(new PlotSettings(
                    plotId,
                    required(plot, "world"),
                    requiredInt(plot, "origin.x"),
                    requiredInt(plot, "origin.y"),
                    requiredInt(plot, "origin.z"),
                    new PlotTriggerSettings(
                            requiredInt(plot, "trigger.min.x"),
                            requiredInt(plot, "trigger.min.y"),
                            requiredInt(plot, "trigger.min.z"),
                            requiredInt(plot, "trigger.max.x"),
                            requiredInt(plot, "trigger.max.y"),
                            requiredInt(plot, "trigger.max.z"))));
        }
        DrinkDispenserSettings drinkDispensers = loadDrinkDispensers(config);

        return new PluginSettings(
                schemaVersion,
                database,
                maxActivePlots,
                maxCustomers,
                worldOperations,
                supplyRuntime,
                plots,
                drinkDispensers);
    }

    private static DrinkDispenserSettings loadDrinkDispensers(FileConfiguration config) {
        ConfigurationSection dispenserSection =
                config.getConfigurationSection("drink-dispensers");
        if (dispenserSection == null) {
            return new DrinkDispenserSettings(3, List.of());
        }
        ConfigurationSection stationsSection =
                config.getConfigurationSection("drink-dispensers.stations");
        if (stationsSection == null) {
            throw new IllegalArgumentException("drink-dispensers.stations must be a section");
        }
        int fillSeconds = requiredInt(config, "drink-dispensers.fill-seconds");
        List<DrinkStationSettings> stations = new ArrayList<>();
        for (String stationId : stationsSection.getKeys(false)) {
            ConfigurationSection station = stationsSection.getConfigurationSection(stationId);
            if (station == null) {
                throw new IllegalArgumentException("Invalid drink station section: " + stationId);
            }
            stations.add(new DrinkStationSettings(
                    stationId,
                    required(station, "world"),
                    requiredInt(station, "location.x"),
                    requiredInt(station, "location.y"),
                    requiredInt(station, "location.z"),
                    DrinkType.parse(required(station, "drink"),
                            "drink-dispensers.stations." + stationId + ".drink")));
        }
        stations.sort(Comparator.comparing(DrinkStationSettings::stationId));
        return new DrinkDispenserSettings(fillSeconds, stations);
    }

    private static String required(ConfigurationSection config, String path) {
        String value = config.getString(path, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(path + " must not be blank");
        }
        return value;
    }

    private static int requiredInt(ConfigurationSection config, String path) {
        if (!config.isInt(path)) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return config.getInt(path);
    }
}
