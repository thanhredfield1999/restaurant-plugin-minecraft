package vn.restauranttycoon.dish;

import java.util.Objects;
import java.util.Optional;

public record DishInventorySlot(Kind kind, Optional<DishItemToken> token) {
    public enum Kind {
        EMPTY,
        VANILLA,
        INVALID_TOKEN,
        TOKEN
    }

    public DishInventorySlot {
        Objects.requireNonNull(kind, "kind");
        token = Objects.requireNonNull(token, "token");
        if ((kind == Kind.TOKEN) != token.isPresent()) {
            throw new IllegalArgumentException("Only TOKEN slots may contain a dish token");
        }
    }

    public static DishInventorySlot empty() {
        return new DishInventorySlot(Kind.EMPTY, Optional.empty());
    }

    public static DishInventorySlot vanilla() {
        return new DishInventorySlot(Kind.VANILLA, Optional.empty());
    }

    public static DishInventorySlot invalidToken() {
        return new DishInventorySlot(Kind.INVALID_TOKEN, Optional.empty());
    }

    public static DishInventorySlot token(DishItemToken token) {
        return new DishInventorySlot(Kind.TOKEN, Optional.of(token));
    }
}