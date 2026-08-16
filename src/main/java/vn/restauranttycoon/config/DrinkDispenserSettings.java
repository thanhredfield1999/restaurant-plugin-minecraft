package vn.restauranttycoon.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DrinkDispenserSettings(
        int fillSeconds,
        List<DrinkStationSettings> stations
) {
    public DrinkDispenserSettings {
        if (fillSeconds < 1 || fillSeconds > 300) {
            throw new IllegalArgumentException("drink-dispensers.fill-seconds must be between 1 and 300");
        }
        stations = List.copyOf(stations);
        Set<StationLocation> locations = new HashSet<>();
        for (DrinkStationSettings station : stations) {
            StationLocation location = new StationLocation(
                    station.worldName(), station.x(), station.y(), station.z());
            if (!locations.add(location)) {
                throw new IllegalArgumentException("drink stations must not share a block location");
            }
        }
    }

    private record StationLocation(String worldName, int x, int y, int z) {
    }
}
