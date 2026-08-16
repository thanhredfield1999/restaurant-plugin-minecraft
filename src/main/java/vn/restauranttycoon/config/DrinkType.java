package vn.restauranttycoon.config;

import java.util.Locale;

public enum DrinkType {
    WATER;

    public static DrinkType parse(String value, String path) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(path + " must be a supported drink type", exception);
        }
    }
}
