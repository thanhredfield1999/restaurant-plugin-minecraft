package vn.restauranttycoon.supplysetup;

import java.util.Map;

@FunctionalInterface
public interface SupplySetupMessages {
    String text(String key, Map<String, String> placeholders);

    default String text(String key) {
        return text(key, Map.of());
    }
}
