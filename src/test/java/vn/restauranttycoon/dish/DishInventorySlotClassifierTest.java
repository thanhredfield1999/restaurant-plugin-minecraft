package vn.restauranttycoon.dish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DishInventorySlotClassifierTest {
    private final DishInventorySlotClassifier classifier = new DishInventorySlotClassifier();

    @Test
    void classifiesEmptyVanillaAndMalformedMarker() {
        assertEquals(DishInventorySlot.empty(),
                classifier.classify(0, DishItemPdcCodec.ReadResult.absent()));
        assertEquals(DishInventorySlot.vanilla(),
                classifier.classify(4, DishItemPdcCodec.ReadResult.absent()));
        assertEquals(DishInventorySlot.invalidToken(),
                classifier.classify(1, DishItemPdcCodec.ReadResult.invalid()));
        assertEquals(DishInventorySlot.invalidToken(),
                classifier.classify(0, DishItemPdcCodec.ReadResult.invalid()));
    }

    @Test
    void onlyAcceptsOnePhysicalItemPerEntitlementToken() {
        DishItemToken token = new DishItemToken(
                UUID.randomUUID(), UUID.randomUUID(), 2, "bread", 1);

        assertEquals(DishInventorySlot.token(token),
                classifier.classify(1, DishItemPdcCodec.ReadResult.valid(token)));
        assertEquals(DishInventorySlot.invalidToken(),
                classifier.classify(2, DishItemPdcCodec.ReadResult.valid(token)));
        assertEquals(DishInventorySlot.invalidToken(),
                classifier.classify(64, DishItemPdcCodec.ReadResult.valid(token)));
    }

    @Test
    void rejectsNegativeAmountButTreatsOversizedUnmarkedStackAsVanilla() {
        assertThrows(IllegalArgumentException.class,
                () -> classifier.classify(-1, DishItemPdcCodec.ReadResult.absent()));
        assertEquals(DishInventorySlot.vanilla(),
                classifier.classify(65, DishItemPdcCodec.ReadResult.absent()));
        assertEquals(DishInventorySlot.invalidToken(),
                classifier.classify(65, DishItemPdcCodec.ReadResult.invalid()));
    }
}
