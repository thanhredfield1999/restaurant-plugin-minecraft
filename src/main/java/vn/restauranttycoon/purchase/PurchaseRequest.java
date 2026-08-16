package vn.restauranttycoon.purchase;

import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.OperationKey;

public record PurchaseRequest(
        UUID accountId,
        OperationKey operationKey,
        String unlockId,
        long definitionVersion,
        CurrencyAmount price,
        String plotId,
        long plotFenceToken,
        long targetStageRevision
) {
    public PurchaseRequest {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(operationKey, "operationKey");
        Objects.requireNonNull(price, "price");
        unlockId = identifier(unlockId, "unlockId");
        plotId = identifier(plotId, "plotId");
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("definitionVersion must be positive");
        }
        if (price.units() < 1) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (plotFenceToken < 0) {
            throw new IllegalArgumentException("plotFenceToken cannot be negative");
        }
        if (targetStageRevision < 1) {
            throw new IllegalArgumentException("targetStageRevision must be positive");
        }
    }

    private static String identifier(String value, String name) {
        if (value == null || !value.matches("[a-z0-9][a-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(name + " must be a lowercase identifier of at most 64 characters");
        }
        return value;
    }
}
