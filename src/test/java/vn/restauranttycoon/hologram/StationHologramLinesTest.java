package vn.restauranttycoon.hologram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.config.DrinkStationSettings;
import vn.restauranttycoon.config.DrinkType;
import vn.restauranttycoon.drink.DrinkStationProgress;
import vn.restauranttycoon.drink.DrinkStationStatus;

class StationHologramLinesTest {
    private static final DrinkStationSettings STATION = new DrinkStationSettings(
            "water_1", "world", 10, 64, -5, DrinkType.WATER);

    @Test
    void rendersIdleStationWithoutFalseProgress() {
        assertEquals(
                List.of("&bQuầy nước", "&fwater_1", "&7Sẵn sàng sử dụng"),
                StationHologramLines.forDrinkStation(
                        new DrinkStationStatus(STATION, DrinkStationProgress.idle())));
    }

    @Test
    void rendersCurrentFillProgress() {
        assertEquals(
                List.of("&bQuầy nước", "&fwater_1", "&7Đang rót: 42%"),
                StationHologramLines.forDrinkStation(
                        new DrinkStationStatus(STATION, new DrinkStationProgress(true, 42))));
    }
}
