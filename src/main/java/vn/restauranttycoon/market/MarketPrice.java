package vn.restauranttycoon.market;

import java.util.Objects;
import java.util.UUID;

public record MarketPrice(
        UUID cycleId,
        long cycleNumber,
        String sku,
        long basePrice,
        long unitPrice,
        double demandFactor,
        double supplyFactor,
        long quantityDemanded,
        long quantitySupplied
) {
    public MarketPrice {
        Objects.requireNonNull(cycleId, "cycleId");
        Objects.requireNonNull(sku, "sku");
        if (basePrice <= 0 || unitPrice <= 0 || demandFactor <= 0 || supplyFactor <= 0
                || quantityDemanded < 0 || quantitySupplied < 0) {
            throw new IllegalArgumentException("Invalid market price state");
        }
    }
}

record MarketEventResult(boolean duplicate, UUID eventId, MarketPrice price) {
}

final class MarketOperationConflictException extends RuntimeException {
    MarketOperationConflictException(String message) {
        super(message);
    }
}
