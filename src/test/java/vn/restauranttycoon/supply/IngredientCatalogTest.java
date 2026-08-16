package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngredientCatalogTest {
    @Test
    void acceptsStableSkuAndRejectsInvalidPrice() {
        IngredientCatalog catalog = new IngredientCatalog(
                3,
                new IngredientDefinition("tomato", "Tomato", IngredientUnit.PIECE, 25, 100));

        assertEquals(3, catalog.version());
        assertEquals(25, catalog.require("tomato").unitPrice().units());
        assertThrows(IllegalArgumentException.class, () -> new IngredientDefinition(
                "tomato", "Tomato", IngredientUnit.PIECE, -1, 100));
    }

    @Test
    void rejectsDuplicateSkuAndUnknownLookup() {
        IngredientDefinition tomato = new IngredientDefinition(
                "tomato", "Tomato", IngredientUnit.PIECE, 25, 100);
        assertThrows(IllegalArgumentException.class, () -> new IngredientCatalog(
                1, tomato, tomato));
        IngredientCatalog catalog = new IngredientCatalog(1, tomato);
        assertThrows(IllegalArgumentException.class, () -> catalog.require("missing"));
    }
}
