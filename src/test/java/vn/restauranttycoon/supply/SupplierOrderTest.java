package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierOrderTest {
    private static final UUID RESTAURANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private IngredientCatalog catalog() {
        return new IngredientCatalog(7,
                new IngredientDefinition("tomato", "Tomato", IngredientUnit.PIECE, 25, 100),
                new IngredientDefinition("rice", "Rice", IngredientUnit.GRAM, 3, 1000));
    }

    @Test
    void snapshotsPriceAndCalculatesTotal() {
        SupplierOrder order = SupplierOrder.draft(RESTAURANT, PLAYER, catalog())
                .addLine("tomato", 4)
                .addLine("rice", 100)
                .submit(OPERATION);

        assertEquals(400, order.total().units());
        assertEquals(7, order.catalogVersion());
        assertEquals(SupplierOrderState.SUBMITTED, order.state());
    }

    @Test
    void rejectsDuplicateSkuAndQuantityAboveCatalogLimit() {
        SupplierOrder order = SupplierOrder.draft(RESTAURANT, PLAYER, catalog());
        assertThrows(IllegalArgumentException.class, () -> order.addLine("tomato", 1).addLine("tomato", 1));
        assertThrows(IllegalArgumentException.class, () -> order.addLine("tomato", 101));
    }

    @Test
    void sameSubmitOperationIsIdempotentButDifferentPayloadConflicts() {
        SupplierOrder order = SupplierOrder.draft(RESTAURANT, PLAYER, catalog()).addLine("tomato", 2);
        SupplierOrder submitted = order.submit(OPERATION);
        assertEquals(submitted, submitted.submit(OPERATION));
        assertThrows(OperationConflictException.class, () ->
                submitted.cancel(OPERATION));
    }
}
