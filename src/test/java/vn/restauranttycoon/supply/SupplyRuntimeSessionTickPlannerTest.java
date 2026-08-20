package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyRuntimeSessionTickPlannerTest {
    @Test
    void movementTickDoesNotTransitionBeforeArrival() {
        SupplyRuntimeSessionTickPlan plan = SupplyRuntimeSessionTickPlanner.plan(
                SupplyRuntimeSessionTickContext.MOVING, false);

        assertEquals(SupplyRuntimeSessionTickPlan.Action.MOVE, plan.action());
        assertTrue(!plan.renewLease());
    }

    @Test
    void leaseRenewalIsIndependentFromMovement() {
        SupplyRuntimeSessionTickPlan plan = SupplyRuntimeSessionTickPlanner.plan(
                SupplyRuntimeSessionTickContext.MOVING, true);

        assertEquals(SupplyRuntimeSessionTickPlan.Action.MOVE, plan.action());
        assertTrue(plan.renewLease());
    }

    @Test
    void arrivalRequestsCasOnlyAfterAdapterReportsArrival() {
        SupplyRuntimeSessionTickPlan plan = SupplyRuntimeSessionTickPlanner.plan(
                SupplyRuntimeSessionTickContext.ARRIVED, true);

        assertEquals(SupplyRuntimeSessionTickPlan.Action.TRANSITION, plan.action());
        assertTrue(plan.renewLease());
    }
}
