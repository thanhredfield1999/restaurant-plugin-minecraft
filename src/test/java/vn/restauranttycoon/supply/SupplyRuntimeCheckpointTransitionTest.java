package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class SupplyRuntimeCheckpointTransitionTest {
    @Test
    void allowsOnlyImmediateForwardTransition() {
        assertTrue(SupplyRuntimeCheckpointTransition.isNext("DELIVERY_ENTRY", "ROUTE_WAYPOINT"));
        assertTrue(SupplyRuntimeCheckpointTransition.isNext("ROUTE_WAYPOINT", "ROUTE_WAYPOINT"));
        assertFalse(SupplyRuntimeCheckpointTransition.isNext("DELIVERY_ENTRY", "UNLOAD_POINT"));
        assertFalse(SupplyRuntimeCheckpointTransition.isNext("UNLOAD_POINT", "DELIVERY_ENTRY"));
    }

    @Test
    void pendingManualCannotAdvanceWithoutExplicitRecovery() {
        assertFalse(SupplyRuntimeCheckpointTransition.isNext("PENDING_MANUAL", "DELIVERY_ENTRY"));
        assertFalse(SupplyRuntimeCheckpointTransition.isNext("DELIVERY_ENTRY", "PENDING_MANUAL"));
    }
}
