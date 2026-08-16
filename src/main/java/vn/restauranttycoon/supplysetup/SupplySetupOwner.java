package vn.restauranttycoon.supplysetup;

import java.util.Objects;

public record SupplySetupOwner(SupplySetupScope scope, String ownerId) {
    private static final String CENTRAL_OWNER_ID = "central_supplier";

    public SupplySetupOwner {
        Objects.requireNonNull(scope, "scope");
        if (ownerId == null || !ownerId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(
                    "ownerId must be a safe identifier of at most 64 characters");
        }
        if (scope == SupplySetupScope.CENTRAL_SUPPLIER && !CENTRAL_OWNER_ID.equals(ownerId)) {
            throw new IllegalArgumentException("central supplier must use the canonical ownerId");
        }
    }

    public static SupplySetupOwner centralSupplier() {
        return new SupplySetupOwner(SupplySetupScope.CENTRAL_SUPPLIER, CENTRAL_OWNER_ID);
    }

    public static SupplySetupOwner restaurant(String plotId) {
        return new SupplySetupOwner(SupplySetupScope.RESTAURANT, plotId);
    }
}
