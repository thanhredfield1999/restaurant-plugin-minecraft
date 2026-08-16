package vn.restauranttycoon.dish;

import java.util.Objects;

public final class DishInventorySlotClassifier {
    public DishInventorySlot classify(int amount, DishItemPdcCodec.ReadResult tokenResult) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount cannot be negative");
        }
        Objects.requireNonNull(tokenResult, "tokenResult");
        if (amount == 0) {
            return tokenResult.status() == DishItemPdcCodec.ReadStatus.ABSENT
                    ? DishInventorySlot.empty()
                    : DishInventorySlot.invalidToken();
        }
        return switch (tokenResult.status()) {
            case ABSENT -> DishInventorySlot.vanilla();
            case INVALID -> DishInventorySlot.invalidToken();
            case VALID -> amount == 1
                    ? DishInventorySlot.token(tokenResult.token().orElseThrow())
                    : DishInventorySlot.invalidToken();
        };
    }
}
