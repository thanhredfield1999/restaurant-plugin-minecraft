package vn.restauranttycoon.drink;

public record DrinkStationProgress(boolean active, int percent) {
    public DrinkStationProgress {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("percent must be between 0 and 100");
        }
        if (!active && percent != 0) {
            throw new IllegalArgumentException("inactive station progress must be zero");
        }
    }

    public static DrinkStationProgress idle() {
        return new DrinkStationProgress(false, 0);
    }
}
