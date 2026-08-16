package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.economy.CurrencyAmount;

public record SupplyOrderReceipt(UUID orderId, CurrencyAmount balance, boolean duplicate) {
    public SupplyOrderReceipt {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(balance, "balance");
    }
}