package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeTransitionCoordinatorTest {
    @Test
    void plansAndExecutesTransitionOnProvidedExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            SupplyRuntimeClaim claim = claim();
            SupplyRuntimeProjection projection = projection();
            SupplyRuntimeTransitionCoordinator coordinator = new SupplyRuntimeTransitionCoordinator(
                    executor,
                    new SupplyRuntimeTransitionApplier(request -> {
                        assertEquals(claim, request.claim());
                        assertEquals("DELIVERY_STOP", request.command().nextStage());
                        return SupplyRuntimeTransitionResult.APPLIED;
                    }));

            SupplyRuntimeTransitionResult result = coordinator.transition(
                    claim, projection, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")).get(2, TimeUnit.SECONDS);

            assertEquals(SupplyRuntimeTransitionResult.APPLIED, result);
            coordinator.close();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void closedCoordinatorRejectsNewTransition() throws Exception {
        SupplyRuntimeTransitionCoordinator coordinator = new SupplyRuntimeTransitionCoordinator(
                Runnable::run, new SupplyRuntimeTransitionApplier(
                        request -> SupplyRuntimeTransitionResult.APPLIED));
        coordinator.close();

        assertEquals(SupplyRuntimeTransitionCoordinator.Result.CLOSED,
                coordinator.tryTransition(claim(), projection(), UUID.randomUUID()));
    }

    private static SupplyRuntimeClaim claim() {
        return new SupplyRuntimeClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT, 1,
                "worker", UUID.randomUUID(), Instant.now().plusSeconds(60));
    }

    private static SupplyRuntimeProjection projection() {
        SupplyDeliveryJourneySnapshot journey = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 1, 65, 1, 0, 0)),
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_STOP,
                        new SupplySetupPosition("world", 2, 65, 1, 0, 0))));
        return new SupplyRuntimeProjection(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1,
                "DELIVERY_ENTRY", 0, journey);
    }
}
