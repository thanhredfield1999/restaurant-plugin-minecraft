package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.OperationKey;

class SupplierPaymentTest {
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Test
    void capturesOnceAndRefundsOnce() {
        SupplierPayment payment = SupplierPayment.open(ORDER, new CurrencyAmount(250));
        OperationKey capture = OperationKey.create();
        OperationKey refund = OperationKey.create();

        payment = payment.capture(capture);
        assertEquals(SupplierPaymentState.CAPTURED, payment.state());
        assertEquals(payment, payment.capture(capture));
        payment = payment.refund(refund);
        assertEquals(SupplierPaymentState.REFUNDED, payment.state());
        assertEquals(payment, payment.refund(refund));
    }

    @Test
    void rejectsInvalidTransitionsAndDifferentRetryKeys() {
        SupplierPayment payment = SupplierPayment.open(ORDER, new CurrencyAmount(250));
        OperationKey capture = OperationKey.create();
        payment = payment.capture(capture);
        SupplierPayment captured = payment;

        assertThrows(OperationConflictException.class, () -> captured.capture(OperationKey.create()));
        assertThrows(IllegalStateException.class, () -> captured.refund(capture));
        assertThrows(IllegalArgumentException.class, () ->
                SupplierPayment.open(ORDER, new CurrencyAmount(0)));
    }
}
