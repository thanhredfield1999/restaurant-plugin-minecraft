package vn.restauranttycoon.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CurrencyAmountTest {
    @Test
    void rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> new CurrencyAmount(-1));
    }

    @Test
    void addsUsingExactLongArithmetic() {
        assertEquals(new CurrencyAmount(15), new CurrencyAmount(10).add(new CurrencyAmount(5)));
        assertThrows(ArithmeticException.class,
                () -> new CurrencyAmount(Long.MAX_VALUE).add(new CurrencyAmount(1)));
    }

    @Test
    void preventsNegativeBalance() {
        assertEquals(new CurrencyAmount(4), new CurrencyAmount(10).subtract(new CurrencyAmount(6)));
        assertThrows(InsufficientFundsException.class,
                () -> new CurrencyAmount(5).subtract(new CurrencyAmount(6)));
    }
}
