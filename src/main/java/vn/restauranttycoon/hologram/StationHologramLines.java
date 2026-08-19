package vn.restauranttycoon.hologram;

import java.util.List;
import vn.restauranttycoon.drink.DrinkStationStatus;

public final class StationHologramLines {
    private StationHologramLines() {
    }

    public static List<String> forDrinkStation(DrinkStationStatus status) {
        String state = status.progress().active()
                ? "Đang rót: " + status.progress().percent() + "%"
                : "Sẵn sàng sử dụng";
        return List.of(
                "&bQuầy nước",
                "&f" + status.station().stationId(),
                "&7" + state);
    }
}
