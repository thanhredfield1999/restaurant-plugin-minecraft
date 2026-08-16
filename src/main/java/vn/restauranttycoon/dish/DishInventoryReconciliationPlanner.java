package vn.restauranttycoon.dish;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DishInventoryReconciliationPlanner {
    public DishInventoryReconciliationPlan plan(
            UUID playerId,
            List<DishInventorySlot> slots,
            Map<UUID, DishEntitlement> projectionEntitlements,
            Map<UUID, DishEntitlement> tokenValidationEntitlements
    ) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(slots, "slots");
        Objects.requireNonNull(projectionEntitlements, "projectionEntitlements");
        Objects.requireNonNull(tokenValidationEntitlements, "tokenValidationEntitlements");

        Map<UUID, DishItemToken> projected = expectedTokens(playerId, projectionEntitlements);
        Map<UUID, DishEntitlement> validationSnapshot = new HashMap<>(
                tokenValidationEntitlements);
        for (Map.Entry<UUID, DishEntitlement> entry : projectionEntitlements.entrySet()) {
            DishEntitlement previous = validationSnapshot.putIfAbsent(
                    entry.getKey(), entry.getValue());
            if (previous != null && !previous.equals(entry.getValue())) {
                throw new IllegalArgumentException(
                        "Projection and token-validation snapshots disagree");
            }
        }
        Map<UUID, DishItemToken> validInventoryTokens = expectedTokens(
                playerId, validationSnapshot);
        Set<UUID> retained = new HashSet<>();
        Set<Integer> slotsToClear = new LinkedHashSet<>();
        Map<UUID, Integer> preferredReplacementSlots = new HashMap<>();
        List<Integer> emptySlots = new ArrayList<>();

        for (int slot = 0; slot < slots.size(); slot++) {
            DishInventorySlot inventorySlot = Objects.requireNonNull(slots.get(slot), "slot");
            if (inventorySlot.kind() == DishInventorySlot.Kind.EMPTY) {
                emptySlots.add(slot);
                continue;
            }
            if (inventorySlot.kind() == DishInventorySlot.Kind.VANILLA) {
                continue;
            }
            if (inventorySlot.kind() == DishInventorySlot.Kind.INVALID_TOKEN) {
                slotsToClear.add(slot);
                emptySlots.add(slot);
                continue;
            }
            DishItemToken actual = inventorySlot.token().orElseThrow();
            DishItemToken authoritative = validInventoryTokens.get(actual.entitlementId());
            if (actual.equals(authoritative) && retained.add(actual.entitlementId())) {
                continue;
            }
            slotsToClear.add(slot);
            if (projected.containsKey(actual.entitlementId())
                    && !retained.contains(actual.entitlementId())) {
                preferredReplacementSlots.putIfAbsent(actual.entitlementId(), slot);
            } else {
                emptySlots.add(slot);
            }
        }

        List<DishInventoryGrant> grants = new ArrayList<>();
        List<DishItemToken> missing = projected.values().stream()
                .filter(token -> !retained.contains(token.entitlementId()))
                .sorted(Comparator.comparing(token -> token.entitlementId().toString()))
                .toList();
        for (DishItemToken token : missing) {
            Integer slot = preferredReplacementSlots.remove(token.entitlementId());
            if (slot == null && !emptySlots.isEmpty()) {
                slot = emptySlots.remove(0);
            }
            if (slot == null) {
                continue;
            }
            grants.add(new DishInventoryGrant(slot, token));
        }
        return new DishInventoryReconciliationPlan(slots, slotsToClear, grants);
    }

    private Map<UUID, DishItemToken> expectedTokens(
            UUID playerId, Map<UUID, DishEntitlement> authoritativeEntitlements) {
        Map<UUID, DishItemToken> expected = new HashMap<>();
        for (Map.Entry<UUID, DishEntitlement> entry : authoritativeEntitlements.entrySet()) {
            DishEntitlement entitlement = Objects.requireNonNull(entry.getValue(), "entitlement");
            if (!entry.getKey().equals(entitlement.entitlementId())) {
                throw new IllegalArgumentException("Entitlement map key does not match its value");
            }
            if (entitlement.state() == DishEntitlementState.CLAIMED
                    && entitlement.holderId().equals(Optional.of(playerId))) {
                expected.put(entitlement.entitlementId(), DishItemToken.from(entitlement));
            }
        }
        return expected;
    }
}
