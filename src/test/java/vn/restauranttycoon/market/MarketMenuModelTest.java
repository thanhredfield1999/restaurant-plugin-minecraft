package vn.restauranttycoon.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.supply.IngredientCatalog;
import vn.restauranttycoon.supply.IngredientDefinition;
import vn.restauranttycoon.supply.IngredientUnit;

class MarketMenuModelTest {
    @Test
    void usesDatabaseMarketPriceAndShowsTrend() {
        IngredientCatalog catalog = new IngredientCatalog(3,
                new IngredientDefinition("tomato", "Cà chua", IngredientUnit.PIECE, 25, 100));
        MarketPrice price = new MarketPrice(UUID.randomUUID(), 1, "tomato", 25, 30,
                1.1, 1.0, 2, 0);

        MarketMenuEntry entry = MarketMenuModel.from(catalog, List.of(price)).require("tomato");

        assertEquals(new CurrencyAmount(30), new CurrencyAmount(entry.price().unitPrice()));
        assertEquals(5, entry.priceDeltaFromBase());
        assertEquals("Tăng", entry.trend());
    }

    @Test
    void missingDatabasePriceFailsClosed() {
        IngredientCatalog catalog = new IngredientCatalog(3,
                new IngredientDefinition("tomato", "Cà chua", IngredientUnit.PIECE, 25, 100));

        assertThrows(IllegalArgumentException.class, () -> MarketMenuModel.from(catalog, List.of()));
    }

    @Test
    void quantityUsesDatabasePriceAndRespectsMaximum() {
        IngredientCatalog catalog = new IngredientCatalog(3,
                new IngredientDefinition("tomato", "Cà chua", IngredientUnit.PIECE, 25, 5));
        MarketMenuModel model = MarketMenuModel.from(catalog, List.of(
                new MarketPrice(UUID.randomUUID(), 1, "tomato", 25, 30, 1, 1, 0, 0)));

        MarketMenuModel selected = model.increase("tomato", 2);

        assertEquals(2, selected.quantity("tomato"));
        assertEquals(60, selected.total().units());
        assertThrows(IllegalArgumentException.class, () -> selected.increase("tomato", 4));
        assertEquals(0, selected.decrease("tomato", 5).quantity("tomato"));
    }
}
