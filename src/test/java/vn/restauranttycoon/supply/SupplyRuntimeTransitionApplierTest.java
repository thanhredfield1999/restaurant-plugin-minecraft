package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupplyRuntimeTransitionApplierTest {
    @Test
    void forwardsClaimAndAllCommandFieldsToStore() throws Exception {
        SupplyRuntimeClaim claim = claim();
        SupplyRuntimeTransitionCommand command = new SupplyRuntimeTransitionCommand(
                4, "DELIVERY_ENTRY", 0, "DELIVERY_STOP", 0,
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        AtomicReference<SupplyRuntimeTransitionRequest> received = new AtomicReference<>();
        SupplyRuntimeTransitionApplier applier = new SupplyRuntimeTransitionApplier(
                request -> {
                    received.set(request);
                    return SupplyRuntimeTransitionResult.APPLIED;
                });

        SupplyRuntimeTransitionResult result = applier.apply(claim, command);

        assertEquals(SupplyRuntimeTransitionResult.APPLIED, result);
        assertSame(claim, received.get().claim());
        assertEquals(command, received.get().command());
    }

    @Test
    void replayResultIsPreserved() throws Exception {
        SupplyRuntimeTransitionApplier applier = new SupplyRuntimeTransitionApplier(
                ignored -> SupplyRuntimeTransitionResult.IDEMPOTENT_REPLAY);

        assertEquals(SupplyRuntimeTransitionResult.IDEMPOTENT_REPLAY,
                applier.apply(claim(), new SupplyRuntimeTransitionCommand(
                        1, "DELIVERY_ENTRY", 0, "DELIVERY_STOP", 0, UUID.randomUUID())));
    }

    private static SupplyRuntimeClaim claim() {
        return new SupplyRuntimeClaim(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT, 1,
                "test-instance", UUID.randomUUID(), Instant.now().plusSeconds(60));
    }
}
