package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SupplyRuntimeSessionTickExecutorTest {
    @Test
    void movingSessionOnlyMovesAndRenewsWhenDue() {
        UUID shipment = UUID.randomUUID();
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger renewals = new AtomicInteger();
        AtomicInteger transitions = new AtomicInteger();

        SupplyRuntimeSessionTickExecutor.execute(
                SupplyRuntimeSessionTickContext.MOVING,
                true,
                moves::incrementAndGet,
                renewals::incrementAndGet,
                transitions::incrementAndGet,
                () -> { });

        assertEquals(1, moves.get());
        assertEquals(1, renewals.get());
        assertEquals(0, transitions.get());
    }

    @Test
    void arrivalTransitionsAndNeverMoves() {
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger renewals = new AtomicInteger();
        AtomicInteger transitions = new AtomicInteger();

        SupplyRuntimeSessionTickExecutor.execute(
                SupplyRuntimeSessionTickContext.ARRIVED,
                false,
                moves::incrementAndGet,
                renewals::incrementAndGet,
                transitions::incrementAndGet,
                () -> { });

        assertEquals(0, moves.get());
        assertEquals(0, renewals.get());
        assertEquals(1, transitions.get());
    }

    @Test
    void stuckMarksManualAndDoesNotTransition() {
        AtomicInteger transitions = new AtomicInteger();
        AtomicInteger manual = new AtomicInteger();

        SupplyRuntimeSessionTickExecutor.execute(
                SupplyRuntimeSessionTickContext.STUCK,
                true,
                () -> { },
                () -> { },
                transitions::incrementAndGet,
                manual::incrementAndGet);

        assertEquals(0, transitions.get());
        assertEquals(1, manual.get());
    }
}
