package vn.restauranttycoon.config;

public record DrinkStationSettings(
        String stationId,
        String worldName,
        int x,
        int y,
        int z,
        DrinkType drinkType
) {
    public DrinkStationSettings {
        if (stationId == null || !stationId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("drink station ID must be a safe identifier of at most 64 characters");
        }
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("drink station world must not be blank");
        }
        if (drinkType == null) {
            throw new IllegalArgumentException("drink station type must not be null");
        }
    }
}
