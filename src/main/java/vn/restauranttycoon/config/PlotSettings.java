package vn.restauranttycoon.config;

import java.util.Objects;

public record PlotSettings(
        String plotId,
        String worldName,
        int originX,
        int originY,
        int originZ,
        PlotTriggerSettings trigger
) {
    public PlotSettings {
        validateIdentifier(plotId, "plotId");
        if (worldName == null || worldName.isBlank()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        Objects.requireNonNull(trigger, "trigger");
    }

    private static void validateIdentifier(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(name + " must be a safe identifier of at most 64 characters");
        }
    }
}
