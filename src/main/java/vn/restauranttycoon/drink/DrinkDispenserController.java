package vn.restauranttycoon.drink;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DrinkDispenserController {
    private final long fillDuration;
    private final Map<String, ActiveFill> activeFills = new HashMap<>();

    public DrinkDispenserController(long fillDuration) {
        if (fillDuration < 1) {
            throw new IllegalArgumentException("fillDuration must be positive");
        }
        this.fillDuration = fillDuration;
    }

    public DrinkDispenserDecision interact(
            String stationId,
            UUID playerId,
            boolean leverDown,
            boolean mainHandEmpty,
            long now
    ) {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(playerId, "playerId");
        ActiveFill activeFill = activeFills.get(stationId);

        if (!leverDown) {
            if (activeFill != null) {
                if (activeFill.playerId().equals(playerId)) {
                    return new DrinkDispenserDecision(
                            DrinkDispenserOutcome.STILL_FILLING,
                            true,
                            false);
                }
                return new DrinkDispenserDecision(
                        DrinkDispenserOutcome.OWNED_BY_ANOTHER_PLAYER,
                        true,
                        false);
            }
            activeFills.put(stationId, new ActiveFill(playerId, now));
            return new DrinkDispenserDecision(DrinkDispenserOutcome.STARTED, true, false);
        }

        if (activeFill == null) {
            return new DrinkDispenserDecision(DrinkDispenserOutcome.RESET, false, false);
        }
        if (!activeFill.playerId().equals(playerId)) {
            return new DrinkDispenserDecision(
                    DrinkDispenserOutcome.OWNED_BY_ANOTHER_PLAYER,
                    true,
                    false);
        }
        if (now - activeFill.startedAt() < fillDuration) {
            return new DrinkDispenserDecision(DrinkDispenserOutcome.STILL_FILLING, true, false);
        }
        if (!mainHandEmpty) {
            return new DrinkDispenserDecision(
                    DrinkDispenserOutcome.HAND_MUST_BE_EMPTY,
                    true,
                    false);
        }

        activeFills.remove(stationId);
        return new DrinkDispenserDecision(DrinkDispenserOutcome.DISPENSED, false, true);
    }

    public boolean hasActiveFill(String stationId) {
        return activeFills.containsKey(stationId);
    }

    public void clear(String stationId) {
        activeFills.remove(stationId);
    }

    public void clearAll() {
        activeFills.clear();
    }

    private record ActiveFill(UUID playerId, long startedAt) {
    }
}
