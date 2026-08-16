package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SupplyOrderMenuModelTest {
    @Test
    void exposesCatalogEntriesInStableSkuOrderAndUpdatesBoundedQuantity() {
        IngredientCatalog catalog = new IngredientCatalog(1,
                new IngredientDefinition("tomato", "Cà chua", IngredientUnit.PIECE, 25, 64),
                new IngredientDefinition("rice", "Gạo", IngredientUnit.GRAM, 1, 1000));

        SupplyOrderMenuModel model = SupplyOrderMenuModel.empty(catalog)
                .increase("tomato", 1)
                .increase("rice", 50);

        assertEquals(java.util.List.of("rice", "tomato"),
                model.entries().stream().map(SupplyOrderMenuEntry::sku).toList());
        assertEquals(50, model.quantity("rice"));
        assertEquals(1, model.quantity("tomato"));
        assertEquals(75, model.total().units());
        assertThrows(IllegalArgumentException.class, () -> model.increase("tomato", 64));
    }

    @Test
    void decreaseRemovesAZeroQuantityLineAndEmptyOrderCannotSubmit() {
        IngredientCatalog catalog = new IngredientCatalog(1,
                new IngredientDefinition("tomato", "Cà chua", IngredientUnit.PIECE, 25, 64));
        SupplyOrderMenuModel empty = SupplyOrderMenuModel.empty(catalog);

        assertThrows(IllegalStateException.class, empty::selectedLines);
        assertEquals(0, empty.increase("tomato", 1).decrease("tomato", 1).quantity("tomato"));
    }
}