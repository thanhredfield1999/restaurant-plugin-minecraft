package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneySnapshot;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStage;
import vn.restauranttycoon.supplysetup.SupplyDeliveryJourneyStep;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyRuntimeClaimProjectionDispatcherTest {
    @Test
    void loadsProjectionOnClaimCallbackThreadThenQueuesMainThreadHandler() throws Exception {
        SupplyRuntimeClaim claim = claim();
        SupplyRuntimeWork work = new SupplyRuntimeWork(
                claim.shipmentId(), claim.packageId(), claim.restaurantId(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT);
        SupplyRuntimeProjection projection = projection(claim);
        AtomicReference<SupplyRuntimeProjection> received = new AtomicReference<>();
        AtomicReference<String> thread = new AtomicReference<>();
        java.util.ArrayList<Runnable> queued = new java.util.ArrayList<>();

        SupplyRuntimeClaimProjectionDispatcher dispatcher = new SupplyRuntimeClaimProjectionDispatcher(
                ignored -> Optional.of(projection),
                queued::add,
                value -> {
                    received.set(value);
                    thread.set(Thread.currentThread().getName());
                });

        dispatcher.handle(claim);
        assertEquals(1, queued.size());
        queued.get(0).run();
        assertEquals(projection, received.get());
        assertTrue(thread.get() != null);
    }

    @Test
    void missingProjectionDoesNotTouchMainThreadHandler() throws Exception {
        java.util.ArrayList<Runnable> queued = new java.util.ArrayList<>();
        SupplyRuntimeClaimProjectionDispatcher dispatcher = new SupplyRuntimeClaimProjectionDispatcher(
                ignored -> Optional.empty(), queued::add, ignored -> { });

        dispatcher.handle(claim());

        assertTrue(queued.isEmpty());
    }

    private static SupplyRuntimeClaim claim() {
        return new SupplyRuntimeClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT, 1,
                "worker", UUID.randomUUID(), Instant.now().plusSeconds(60));
    }

    private static SupplyRuntimeProjection projection(SupplyRuntimeClaim claim) {
        SupplyDeliveryJourneySnapshot journey = new SupplyDeliveryJourneySnapshot(1, "plot", List.of(
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_ENTRY,
                        new SupplySetupPosition("world", 1, 65, 1, 0, 0)),
                new SupplyDeliveryJourneyStep(SupplyDeliveryJourneyStage.DELIVERY_STOP,
                        new SupplySetupPosition("world", 2, 65, 1, 0, 0))));
        return new SupplyRuntimeProjection(
                claim.shipmentId(), claim.packageId(), claim.restaurantId(), 1,
                "DELIVERY_ENTRY", 0, journey);
    }
}
