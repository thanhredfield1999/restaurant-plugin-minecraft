package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class SupplyOrderMenuLifecycleTest {
    @Test
    void newerOpenInvalidatesOlderCompletionForSamePlayer() {
        SupplyOrderMenuLifecycle lifecycle = new SupplyOrderMenuLifecycle();
        UUID playerId = UUID.randomUUID();

        SupplyOrderMenuLifecycle.Ticket stale = lifecycle.open(playerId).orElseThrow();
        SupplyOrderMenuLifecycle.Ticket current = lifecycle.open(playerId).orElseThrow();

        assertFalse(lifecycle.isCurrent(stale));
        assertTrue(lifecycle.isCurrent(current));
    }

    @Test
    void playerLogoutInvalidatesOutstandingCompletion() {
        SupplyOrderMenuLifecycle lifecycle = new SupplyOrderMenuLifecycle();
        UUID playerId = UUID.randomUUID();
        SupplyOrderMenuLifecycle.Ticket ticket = lifecycle.open(playerId).orElseThrow();

        lifecycle.close(playerId);

        assertFalse(lifecycle.isCurrent(ticket));
    }

    @Test
    void closingLifecycleInvalidatesAllOutstandingCompletionsAndRejectsNewOpen() {
        SupplyOrderMenuLifecycle lifecycle = new SupplyOrderMenuLifecycle();
        SupplyOrderMenuLifecycle.Ticket first = lifecycle.open(UUID.randomUUID()).orElseThrow();
        SupplyOrderMenuLifecycle.Ticket second = lifecycle.open(UUID.randomUUID()).orElseThrow();

        lifecycle.close();

        assertFalse(lifecycle.isCurrent(first));
        assertFalse(lifecycle.isCurrent(second));
        assertFalse(lifecycle.open(UUID.randomUUID()).isPresent());
    }

    @Test
    void dispatchAfterCloseDoesNotCallScheduler() {
        SupplyOrderMenuLifecycle lifecycle = new SupplyOrderMenuLifecycle();
        SupplyOrderMenuLifecycle.Ticket ticket = lifecycle.open(UUID.randomUUID()).orElseThrow();
        AtomicBoolean scheduled = new AtomicBoolean();
        lifecycle.close();

        assertFalse(lifecycle.dispatchIfCurrent(ticket, ignored -> scheduled.set(true), () -> {}));
        assertFalse(scheduled.get());
    }

    @Test
    void queuedTaskDoesNotRunAfterTicketBecomesStale() {
        SupplyOrderMenuLifecycle lifecycle = new SupplyOrderMenuLifecycle();
        UUID playerId = UUID.randomUUID();
        SupplyOrderMenuLifecycle.Ticket ticket = lifecycle.open(playerId).orElseThrow();
        Runnable[] queued = new Runnable[1];
        AtomicBoolean ran = new AtomicBoolean();

        assertTrue(lifecycle.dispatchIfCurrent(ticket, task -> queued[0] = task, () -> ran.set(true)));
        lifecycle.open(playerId);
        queued[0].run();

        assertFalse(ran.get());
    }
}
