package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SupplyRuntimeMainThreadBridgeTest {
    @Test
    void defersWorkToMainThreadExecutor() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<SupplyRuntimeWork> handled = new AtomicReference<>();
        SupplyRuntimeWork work = new SupplyRuntimeWork(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SupplyShipmentState.IN_TRANSIT, SupplyPackageState.IN_TRANSIT);
        SupplyRuntimeMainThreadBridge bridge = new SupplyRuntimeMainThreadBridge(queued::set, handled::set);
        bridge.handle(work);
        assertNull(handled.get());
        queued.get().run();
        assertEquals(work, handled.get());
    }
}
