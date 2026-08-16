package vn.restauranttycoon.supply;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class WarehouseStock {
    private final Map<String, Integer> quantities = new HashMap<>();
    private final Set<UUID> receivedPackages = new HashSet<>();

    public void receive(SupplyPackage supplyPackage) {
        if (!supplyPackage.handedOff()) {
            throw new IllegalStateException("Package must be handed off before receiving");
        }
        if (!receivedPackages.add(supplyPackage.packageId())) {
            return;
        }
        quantities.merge(supplyPackage.sku(), supplyPackage.quantity(), Math::addExact);
    }

    public int quantity(String sku) {
        return quantities.getOrDefault(sku, 0);
    }
}
