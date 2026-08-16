package vn.restauranttycoon.dish;

import java.util.Objects;
import java.util.UUID;

public record DishInventoryReconciliationTicket(
        UUID playerId,
        long pluginEpoch,
        long playerGeneration
) {
    public DishInventoryReconciliationTicket {
        Objects.requireNonNull(playerId, "playerId");
        if (pluginEpoch <= 0) {
            throw new IllegalArgumentException("Plugin epoch must be positive");
        }
        if (playerGeneration <= 0) {
            throw new IllegalArgumentException("Player generation must be positive");
        }
    }
}
