package vn.restauranttycoon.dish;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record DishItemToken(
        UUID entitlementId,
        UUID holderId,
        long stateRevision,
        String recipeId,
        long recipeVersion
) {
    private static final int SCHEMA_VERSION = 1;
    private static final String SEPARATOR = "|";

    public DishItemToken {
        Objects.requireNonNull(entitlementId, "entitlementId");
        Objects.requireNonNull(holderId, "holderId");
        if (stateRevision < 1) {
            throw new IllegalArgumentException("State revision must be positive");
        }
        if (recipeId == null || !recipeId.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Recipe ID must match [a-z0-9_]{1,64}");
        }
        if (recipeVersion < 1) {
            throw new IllegalArgumentException("Recipe version must be positive");
        }
    }

    public static DishItemToken from(DishEntitlement entitlement) {
        Objects.requireNonNull(entitlement, "entitlement");
        if (entitlement.state() != DishEntitlementState.CLAIMED) {
            throw new IllegalArgumentException("Only claimed entitlements can be projected");
        }
        return new DishItemToken(
                entitlement.entitlementId(),
                entitlement.holderId().orElseThrow(),
                entitlement.stateRevision(),
                entitlement.recipeId(),
                entitlement.recipeVersion());
    }

    public String encode() {
        return SCHEMA_VERSION + SEPARATOR
                + entitlementId + SEPARATOR
                + holderId + SEPARATOR
                + stateRevision + SEPARATOR
                + recipeId + SEPARATOR
                + recipeVersion;
    }

    public static Optional<DishItemToken> decode(String encoded) {
        if (encoded == null) {
            return Optional.empty();
        }
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 6 || !fields[0].equals(Integer.toString(SCHEMA_VERSION))) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DishItemToken(
                    UUID.fromString(fields[1]),
                    UUID.fromString(fields[2]),
                    Long.parseLong(fields[3]),
                    fields[4],
                    Long.parseLong(fields[5])));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
