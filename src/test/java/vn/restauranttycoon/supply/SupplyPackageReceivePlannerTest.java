package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SupplyPackageReceivePlannerTest {
    @Test
    void durableHandoffMustPrecedeTokenGrantAndEntityRemoval() {
        assertEquals(
                java.util.List.of(
                        SupplyPackageReceiveAction.DURABLE_HANDOFF,
                        SupplyPackageReceiveAction.GRANT_TOKEN,
                        SupplyPackageReceiveAction.REMOVE_ENTITY),
                SupplyPackageReceivePlanner.plan(false));
    }

    @Test
    void existingTokenSkipsDuplicateGrantAfterDurableRetry() {
        assertEquals(
                java.util.List.of(
                        SupplyPackageReceiveAction.DURABLE_HANDOFF,
                        SupplyPackageReceiveAction.REMOVE_ENTITY),
                SupplyPackageReceivePlanner.plan(true));
    }
}
