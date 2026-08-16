package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseStockTest {
    private static final UUID PACKAGE = UUID.fromString("00000000-0000-0000-0000-000000000031");

    @Test
    void creditsEachPackageExactlyOnceAfterHandoff() {
        WarehouseStock stock = new WarehouseStock();
        SupplyPackage created = SupplyPackage.create(PACKAGE, "tomato", 4);
        assertThrows(IllegalStateException.class, () -> stock.receive(created));

        SupplyPackage handedOff = created.handoff();
        stock.receive(handedOff);
        stock.receive(handedOff);

        assertEquals(4, stock.quantity("tomato"));
    }

    @Test
    void rejectsDuplicateSkuAndNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
                SupplyPackage.create(PACKAGE, "tomato", 0));
        assertEquals(0, new WarehouseStock().quantity("missing"));
    }
}
