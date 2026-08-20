package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeTickExecutorTest {
    private static final UUID SHIPMENT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void movingOnlyCallsEntityMovementAndNeverCheckpointCas() {
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger transitions = new AtomicInteger();
        SupplyRuntimeTickExecutor executor = new SupplyRuntimeTickExecutor(
                (ignored, target) -> moves.incrementAndGet(),
                ignored -> transitions.incrementAndGet());

        executor.execute(projection(), List.of(UUID.randomUUID()), SupplyVillagerMovementOutcome.MOVING);

        assertEquals(1, moves.get());
        assertEquals(0, transitions.get());
    }

    @Test
    void arrivedCallsCheckpointCasAndNeverMovement() {
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger transitions = new AtomicInteger();
        SupplyRuntimeTickExecutor executor = new SupplyRuntimeTickExecutor(
                (ignored, target) -> moves.incrementAndGet(),
                ignored -> transitions.incrementAndGet());

        executor.execute(projection(), List.of(UUID.randomUUID()), SupplyVillagerMovementOutcome.ARRIVED);

        assertEquals(0, moves.get());
        assertEquals(1, transitions.get());
    }

    @Test
    void duplicateCandidatesFailClosedWithoutMovementOrTransition() {
        AtomicInteger moves = new AtomicInteger();
        AtomicInteger transitions = new AtomicInteger();
        SupplyRuntimeTickExecutor executor = new SupplyRuntimeTickExecutor(
                (ignored, target) -> moves.incrementAndGet(),
                ignored -> transitions.incrementAndGet());

        executor.execute(projection(), List.of(UUID.randomUUID(), UUID.randomUUID()), SupplyVillagerMovementOutcome.MOVING);

        assertEquals(0, moves.get());
        assertEquals(0, transitions.get());
    }

    private static SupplyRuntimeProjection projection() {
        SupplyDeliveryJourneySnapshot journey = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(
                        SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 4, 65, 6, 90, 0)),
                new SupplyDeliveryJourneyStep(
                        SupplyDeliveryJourneyStage.DELIVERY_STOP,
                        new SupplySetupPosition("world", 8, 65, 6, 90, 0))));
        return new SupplyRuntimeProjection(
                SHIPMENT, UUID.randomUUID(), UUID.randomUUID(), 1, "DELIVERY_ENTRY", 0, journey);
    }
}
