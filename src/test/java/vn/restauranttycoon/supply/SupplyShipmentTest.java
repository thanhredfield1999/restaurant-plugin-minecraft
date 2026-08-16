package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyShipmentTest {
    private static final UUID SHIPMENT = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Test
    void followsDeliveryLifecycleExactlyOnce() {
        SupplyShipment shipment = SupplyShipment.create(SHIPMENT);
        shipment = shipment.dispatch();
        shipment = shipment.arrive();
        shipment = shipment.handoff();

        assertEquals(SupplyShipmentState.HANDED_OFF, shipment.state());
        assertEquals(shipment, shipment.handoff());
    }

    @Test
    void rejectsInvalidTransitions() {
        SupplyShipment shipment = SupplyShipment.create(SHIPMENT);
        assertThrows(IllegalStateException.class, shipment::arrive);
        assertThrows(IllegalStateException.class, shipment::handoff);
        assertThrows(IllegalStateException.class, shipment.dispatch()::cancel);
    }
}
