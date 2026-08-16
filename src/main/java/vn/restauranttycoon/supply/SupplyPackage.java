package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;

public final class SupplyPackage {
    private final UUID packageId;
    private final String sku;
    private final int quantity;
    private final boolean handedOff;

    private SupplyPackage(UUID packageId, String sku, int quantity, boolean handedOff) {
        this.packageId = packageId;
        this.sku = sku;
        this.quantity = quantity;
        this.handedOff = handedOff;
    }

    public static SupplyPackage create(UUID packageId, String sku, int quantity) {
        Objects.requireNonNull(packageId, "packageId");
        if (sku == null || sku.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("Package SKU and quantity are required");
        }
        return new SupplyPackage(packageId, sku, quantity, false);
    }

    public SupplyPackage handoff() {
        return handedOff ? this : new SupplyPackage(packageId, sku, quantity, true);
    }

    public UUID packageId() { return packageId; }
    public String sku() { return sku; }
    public int quantity() { return quantity; }
    public boolean handedOff() { return handedOff; }
}
