package vn.restauranttycoon.hologram;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import vn.restauranttycoon.drink.DrinkDispenserListener;
import vn.restauranttycoon.drink.DrinkStationStatus;

public final class DecentDrinkStationHolograms implements AutoCloseable {
    private static final String NAME_PREFIX = "restauranttycoon-drink-";
    private final DrinkDispenserListener stations;
    private final Logger logger;
    private final Map<String, List<String>> renderedLines = new HashMap<>();
    private final Set<String> blockedNames = new HashSet<>();

    public DecentDrinkStationHolograms(DrinkDispenserListener stations) {
        this(stations, Logger.getLogger(DecentDrinkStationHolograms.class.getName()));
    }

    public DecentDrinkStationHolograms(DrinkDispenserListener stations, Logger logger) {
        this.stations = Objects.requireNonNull(stations, "stations");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void refresh() {
        for (DrinkStationStatus status : stations.stationStatuses()) {
            String name = nameFor(status);
            World world = Bukkit.getWorld(status.station().worldName());
            if (world == null) {
                remove(name);
                continue;
            }
            List<String> lines = StationHologramLines.forDrinkStation(status);
            List<String> previous = renderedLines.get(name);
            if (previous == null) {
                if (DHAPI.getHologram(name) != null) {
                    if (blockedNames.add(name)) {
                        logger.warning("Hologram name collision; leaving provider hologram untouched: " + name);
                    }
                    continue;
                }
                blockedNames.remove(name);
                try {
                    DHAPI.createHologram(name, location(world, status), false, lines);
                    renderedLines.put(name, lines);
                } catch (IllegalArgumentException exception) {
                    // Re-check next tick. Never adopt a hologram only by name or prefix.
                }
            } else if (!previous.equals(lines)) {
                Hologram hologram = DHAPI.getHologram(name);
                if (hologram == null) {
                    renderedLines.remove(name);
                    continue;
                }
                DHAPI.setHologramLines(hologram, lines);
                renderedLines.put(name, lines);
            }
        }
    }

    @Override
    public void close() {
        List<String> names = List.copyOf(renderedLines.keySet());
        names.forEach(this::remove);
        renderedLines.clear();
        blockedNames.clear();
    }

    private void remove(String name) {
        if (renderedLines.remove(name) != null) {
            try {
                DHAPI.removeHologram(name);
            } catch (LinkageError | RuntimeException ignored) {
                // Provider cleanup must not prevent remaining owned holograms from being attempted.
            }
        }
    }

    private static String nameFor(DrinkStationStatus status) {
        return NAME_PREFIX + status.station().stationId();
    }

    private static Location location(World world, DrinkStationStatus status) {
        return new Location(
                world,
                status.station().x() + 0.5D,
                status.station().y() + 1.7D,
                status.station().z() + 0.5D);
    }
}
