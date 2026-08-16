package vn.restauranttycoon.onboarding;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class OnboardingLifecycle {
    private final Map<UUID, Long> generations = new HashMap<>();
    private boolean closed;

    public synchronized Ticket open(UUID playerId) {
        if (closed) {
            throw new IllegalStateException("Onboarding lifecycle is closed");
        }
        long generation = Math.incrementExact(generations.getOrDefault(playerId, 0L));
        generations.put(playerId, generation);
        return new Ticket(playerId, generation);
    }

    public synchronized boolean isCurrent(Ticket ticket) {
        return !closed && generations.getOrDefault(ticket.playerId(), 0L) == ticket.generation();
    }

    public synchronized void invalidate(UUID playerId) {
        generations.computeIfPresent(playerId, (ignored, value) -> Math.incrementExact(value));
    }

    public synchronized void close() {
        closed = true;
        generations.clear();
    }

    public record Ticket(UUID playerId, long generation) {
    }
}
