package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SupplyRuntimeMovementActionResolverTest {
    @Test
    void mapsOutcomeWithoutPerformingSideEffects() {
        assertEquals(SupplyRuntimeMovementAction.CONTINUE_MOVING,
                SupplyRuntimeMovementActionResolver.resolve(
                        SupplyVillagerMovementOutcome.MOVING, "DELIVERY_ENTRY"));
        assertEquals(SupplyRuntimeMovementAction.TRANSITION_CHECKPOINT,
                SupplyRuntimeMovementActionResolver.resolve(
                        SupplyVillagerMovementOutcome.ARRIVED, "UNLOAD_POINT"));
        assertEquals(SupplyRuntimeMovementAction.FINALIZE_AND_CLEANUP,
                SupplyRuntimeMovementActionResolver.resolve(
                        SupplyVillagerMovementOutcome.ARRIVED, "DELIVERY_DESPAWN"));
        assertEquals(SupplyRuntimeMovementAction.MARK_PENDING_MANUAL,
                SupplyRuntimeMovementActionResolver.resolve(
                        SupplyVillagerMovementOutcome.STUCK, "DELIVERY_ENTRY"));
    }
}
