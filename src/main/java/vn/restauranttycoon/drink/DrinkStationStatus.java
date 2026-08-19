package vn.restauranttycoon.drink;

import vn.restauranttycoon.config.DrinkStationSettings;

public record DrinkStationStatus(
        DrinkStationSettings station,
        DrinkStationProgress progress
) {
}
