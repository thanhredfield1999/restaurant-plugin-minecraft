package vn.restauranttycoon.economy;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OperationKeyTest {
    @Test
    void createsUniqueKeys() {
        assertNotEquals(OperationKey.create(), OperationKey.create());
    }

    @Test
    void rejectsNullValues() {
        assertThrows(NullPointerException.class, () -> new OperationKey(null));
    }
}
