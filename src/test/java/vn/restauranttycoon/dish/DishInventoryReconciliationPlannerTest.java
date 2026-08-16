package vn.restauranttycoon.dish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DishInventoryReconciliationPlannerTest {
    @Test
    void removesForgedStaleForeignAndDuplicateTokensThenGrantsMissingEntitlement() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement first = claimed(playerId, "bread", 1, 2);
        DishEntitlement second = claimed(playerId, "grilled_cod", 3, 5);
        DishItemToken valid = DishItemToken.from(first);
        DishItemToken stale = new DishItemToken(
                second.entitlementId(), playerId, 4, second.recipeId(), second.recipeVersion());
        DishItemToken foreign = new DishItemToken(
                UUID.randomUUID(), playerId, 1, "bread", 1);
        List<DishInventorySlot> slots = List.of(
                DishInventorySlot.token(valid), DishInventorySlot.token(valid),
                DishInventorySlot.token(stale), DishInventorySlot.token(foreign),
                DishInventorySlot.empty());

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                slots,
                Map.of(first.entitlementId(), first, second.entitlementId(), second),
                Map.of(first.entitlementId(), first, second.entitlementId(), second));

        assertEquals(Set.of(1, 2, 3), plan.slotsToClear());
        assertEquals(List.of(new DishInventoryGrant(2, DishItemToken.from(second))), plan.grants());
    }

    @Test
    void fullInventoryNeverDropsOrClonesMissingEntitlement() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement expected = claimed(playerId, "bread", 1, 1);
        DishItemToken unrelated = new DishItemToken(
                UUID.randomUUID(), playerId, 1, "bread", 1);

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                List.of(DishInventorySlot.token(unrelated)),
                Map.of(expected.entitlementId(), expected),
                Map.of(expected.entitlementId(), expected));

        assertEquals(Set.of(0), plan.slotsToClear());
        assertEquals(List.of(new DishInventoryGrant(0, DishItemToken.from(expected))), plan.grants());
    }

    @Test
    void doesNotProjectAvailableOrConsumedEntitlements() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement available = entitlement(
                DishEntitlementState.AVAILABLE, Optional.empty(), "bread", 0);
        DishEntitlement consumed = entitlement(
                DishEntitlementState.CONSUMED, Optional.of(playerId), "grilled_cod", 2);

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                List.of(DishInventorySlot.empty(), DishInventorySlot.empty()),
                Map.of(available.entitlementId(), available, consumed.entitlementId(), consumed),
                Map.of(available.entitlementId(), available, consumed.entitlementId(), consumed));

        assertEquals(Set.of(), plan.slotsToClear());
        assertEquals(List.of(), plan.grants());
    }

    @Test
    void doesNotOverwriteVanillaItemsWhenInventoryIsFull() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement expected = claimed(playerId, "cake", 1, 1);

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                List.of(DishInventorySlot.vanilla(), DishInventorySlot.vanilla()),
                Map.of(expected.entitlementId(), expected),
                Map.of(expected.entitlementId(), expected));

        assertEquals(Set.of(), plan.slotsToClear());
        assertEquals(List.of(), plan.grants());
    }

    @Test
    void removesInvalidTokenMarkerAndReusesItsSlotForMissingEntitlement() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement expected = claimed(playerId, "bread", 1, 1);

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                List.of(DishInventorySlot.invalidToken()),
                Map.of(expected.entitlementId(), expected),
                Map.of(expected.entitlementId(), expected));

        assertEquals(Set.of(0), plan.slotsToClear());
        assertEquals(List.of(new DishInventoryGrant(0, DishItemToken.from(expected))), plan.grants());
    }

    @Test
    void retainsValidTokenOutsideBoundedProjectionPageWhenIdLookupConfirmsIt() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement outsideProjectionPage = claimed(playerId, "cake", 1, 1);

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                List.of(DishInventorySlot.token(DishItemToken.from(outsideProjectionPage))),
                Map.of(),
                Map.of(outsideProjectionPage.entitlementId(), outsideProjectionPage));

        assertEquals(Set.of(), plan.slotsToClear());
        assertEquals(List.of(), plan.grants());
    }

    @Test
    void clearsWrongHolderRecipeVersionAndConsumedTokens() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement claimed = claimed(playerId, "bread", 2, 3);
        DishEntitlement consumed = entitlement(
                DishEntitlementState.CONSUMED, Optional.of(playerId), "cake", 1, 4);
        List<DishInventorySlot> slots = List.of(
                DishInventorySlot.token(new DishItemToken(
                        claimed.entitlementId(), UUID.randomUUID(), 3, "bread", 2)),
                DishInventorySlot.token(new DishItemToken(
                        claimed.entitlementId(), playerId, 3, "cake", 2)),
                DishInventorySlot.token(new DishItemToken(
                        claimed.entitlementId(), playerId, 3, "bread", 1)),
                DishInventorySlot.token(new DishItemToken(
                        consumed.entitlementId(), playerId, 4, "cake", 1)));

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                slots,
                Map.of(),
                Map.of(claimed.entitlementId(), claimed, consumed.entitlementId(), consumed));

        assertEquals(Set.of(0, 1, 2, 3), plan.slotsToClear());
        assertEquals(List.of(), plan.grants());
    }

    @Test
    void rejectsConflictingProjectionAndValidationSnapshots() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement projected = claimed(playerId, "bread", 1, 2);
        DishEntitlement staleValidation = new DishEntitlement(
                projected.entitlementId(), projected.orderId(), projected.restaurantId(),
                projected.recipeId(), projected.recipeVersion(), DishEntitlementState.CLAIMED,
                projected.holderId(), 1);

        assertThrows(IllegalArgumentException.class,
                () -> new DishInventoryReconciliationPlanner().plan(
                        playerId,
                        List.of(DishInventorySlot.empty()),
                        Map.of(projected.entitlementId(), projected),
                        Map.of(staleValidation.entitlementId(), staleValidation)));
    }

    @Test
    void planOnlyAppliesWhileTheStorageClassificationSnapshotIsUnchanged() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement expected = claimed(playerId, "bread", 1, 1);
        List<DishInventorySlot> snapshot = List.of(
                DishInventorySlot.empty(), DishInventorySlot.vanilla());

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                snapshot,
                Map.of(expected.entitlementId(), expected),
                Map.of(expected.entitlementId(), expected));

        assertTrue(plan.isApplicableTo(snapshot));
        assertFalse(plan.isApplicableTo(List.of(
                DishInventorySlot.vanilla(), DishInventorySlot.empty())));
        assertFalse(plan.isApplicableTo(List.of(DishInventorySlot.empty())));
    }

    @Test
    void semanticSnapshotPreservesChangesWithTheSameSafeMutation() {
        UUID playerId = UUID.randomUUID();
        DishEntitlement expected = claimed(playerId, "bread", 1, 1);
        List<DishInventorySlot> snapshot = List.of(
                DishInventorySlot.vanilla(),
                DishInventorySlot.invalidToken(),
                DishInventorySlot.empty());

        DishInventoryReconciliationPlan plan = new DishInventoryReconciliationPlanner().plan(
                playerId,
                snapshot,
                Map.of(expected.entitlementId(), expected),
                Map.of());

        assertTrue(plan.isApplicableTo(List.of(
                DishInventorySlot.vanilla(),
                DishInventorySlot.invalidToken(),
                DishInventorySlot.empty())));
        assertEquals(Set.of(1), plan.slotsToClear());
        assertEquals(List.of(new DishInventoryGrant(1, DishItemToken.from(expected))),
                plan.grants());
    }

    private static DishEntitlement claimed(
            UUID playerId, String recipeId, long recipeVersion, long revision) {
        return entitlement(DishEntitlementState.CLAIMED, Optional.of(playerId),
                recipeId, recipeVersion, revision);
    }

    private static DishEntitlement entitlement(
            DishEntitlementState state,
            Optional<UUID> holderId,
            String recipeId,
            long revision
    ) {
        return entitlement(state, holderId, recipeId, 1, revision);
    }

    private static DishEntitlement entitlement(
            DishEntitlementState state,
            Optional<UUID> holderId,
            String recipeId,
            long recipeVersion,
            long revision
    ) {
        return new DishEntitlement(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), recipeId,
                recipeVersion, state, holderId, revision);
    }
}
