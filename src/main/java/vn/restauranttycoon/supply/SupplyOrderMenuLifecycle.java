package vn.restauranttycoon.supply;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class SupplyOrderMenuLifecycle implements AutoCloseable {
    private final Map<UUID, Long> playerGenerations = new HashMap<>();
    private long nextGeneration;
    private boolean closed;

    public synchronized Optional<Ticket> open(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (closed) return Optional.empty();
        long generation = nextGeneration = Math.incrementExact(nextGeneration);
        playerGenerations.put(playerId, generation);
        return Optional.of(new Ticket(playerId, generation));
    }

    public synchronized void close(UUID playerId) {
        playerGenerations.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    public synchronized boolean isCurrent(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return !closed && playerGenerations.getOrDefault(ticket.playerId(), 0L) == ticket.generation();
    }

    public synchronized boolean dispatchIfCurrent(
            Ticket ticket,
            Consumer<Runnable> scheduler,
            Runnable task
    ) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(task, "task");
        if (!isCurrent(ticket)) return false;
        scheduler.accept(() -> {
            if (isCurrent(ticket)) task.run();
        });
        return true;
    }

    @Override
    public synchronized void close() {
        closed = true;
        playerGenerations.clear();
    }

    public record Ticket(UUID playerId, long generation) {
        public Ticket {
            Objects.requireNonNull(playerId, "playerId");
            if (generation <= 0) throw new IllegalArgumentException("Generation must be positive");
        }
    }
}
