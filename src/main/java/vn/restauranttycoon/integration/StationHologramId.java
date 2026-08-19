package vn.restauranttycoon.integration;

import java.util.Objects;
import java.util.UUID;

/** Stable DecentHolograms identifier for one configured restaurant station. */
public record StationHologramId(UUID restaurantId, String stationId) {
    public StationHologramId {
        Objects.requireNonNull(restaurantId, "restaurantId");
        if (stationId == null || !stationId.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("stationId must match [A-Za-z0-9_-]{1,64}");
        }
    }

    public String value() {
        return "rt-station-" + restaurantId + "-" + stationId;
    }
}
