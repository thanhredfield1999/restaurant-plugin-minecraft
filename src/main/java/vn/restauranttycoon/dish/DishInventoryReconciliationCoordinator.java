package vn.restauranttycoon.dish;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DishInventoryReconciliationCoordinator {
    private static final long INITIAL_PLUGIN_EPOCH = 1;

    private final Map<UUID, Long> playerGenerations = new HashMap<>();
    private long pluginEpoch = INITIAL_PLUGIN_EPOCH;
    private long nextPlayerGeneration;

    public synchronized DishInventoryReconciliationTicket open(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        long generation = nextGeneration();
        playerGenerations.put(playerId, generation);
        return new DishInventoryReconciliationTicket(playerId, pluginEpoch, generation);
    }

    public synchronized void close(UUID playerId) {
        playerGenerations.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized void advancePluginEpoch() {
        pluginEpoch = Math.incrementExact(pluginEpoch);
        playerGenerations.clear();
    }

    public synchronized boolean isCurrent(DishInventoryReconciliationTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return ticket.pluginEpoch() == pluginEpoch
                && playerGenerations.getOrDefault(ticket.playerId(), 0L)
                        == ticket.playerGeneration();
    }

    private long nextGeneration() {
        nextPlayerGeneration = Math.incrementExact(nextPlayerGeneration);
        return nextPlayerGeneration;
    }
}
