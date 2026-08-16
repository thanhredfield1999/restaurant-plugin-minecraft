package vn.restauranttycoon.dish;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DishInventoryReconciliationCoordinatorTest {
    @Test
    void reconnectInvalidatesCompletionFromPreviousPlayerSession() {
        DishInventoryReconciliationCoordinator coordinator =
                new DishInventoryReconciliationCoordinator();
        UUID playerId = UUID.randomUUID();

        DishInventoryReconciliationTicket stale = coordinator.open(playerId);
        DishInventoryReconciliationTicket current = coordinator.open(playerId);

        assertFalse(coordinator.isCurrent(stale));
        assertTrue(coordinator.isCurrent(current));
    }

    @Test
    void playerLogoutInvalidatesOutstandingCompletion() {
        DishInventoryReconciliationCoordinator coordinator =
                new DishInventoryReconciliationCoordinator();
        UUID playerId = UUID.randomUUID();
        DishInventoryReconciliationTicket ticket = coordinator.open(playerId);

        coordinator.close(playerId);

        assertFalse(coordinator.isCurrent(ticket));
    }

    @Test
    void reconnectAfterLogoutNeverRevalidatesOldCompletion() {
        DishInventoryReconciliationCoordinator coordinator =
                new DishInventoryReconciliationCoordinator();
        UUID playerId = UUID.randomUUID();
        DishInventoryReconciliationTicket stale = coordinator.open(playerId);
        coordinator.close(playerId);

        DishInventoryReconciliationTicket current = coordinator.open(playerId);

        assertFalse(coordinator.isCurrent(stale));
        assertTrue(coordinator.isCurrent(current));
    }

    @Test
    void advancingPluginEpochInvalidatesEveryOutstandingCompletion() {
        DishInventoryReconciliationCoordinator coordinator =
                new DishInventoryReconciliationCoordinator();
        DishInventoryReconciliationTicket first = coordinator.open(UUID.randomUUID());
        DishInventoryReconciliationTicket second = coordinator.open(UUID.randomUUID());

        coordinator.advancePluginEpoch();

        assertFalse(coordinator.isCurrent(first));
        assertFalse(coordinator.isCurrent(second));
    }
}
