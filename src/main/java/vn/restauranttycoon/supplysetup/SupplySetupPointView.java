package vn.restauranttycoon.supplysetup;

import java.util.List;

public record SupplySetupPointView(
        String title,
        List<String> lore,
        SupplySetupPointStatus status
) {
    public SupplySetupPointView {
        lore = List.copyOf(lore);
    }
}
