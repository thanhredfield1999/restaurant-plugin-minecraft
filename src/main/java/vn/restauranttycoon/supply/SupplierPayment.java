package vn.restauranttycoon.supply;

import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.OperationKey;

public final class SupplierPayment {
    private final UUID orderId;
    private final CurrencyAmount amount;
    private final SupplierPaymentState state;
    private final OperationKey captureOperation;
    private final OperationKey refundOperation;

    private SupplierPayment(UUID orderId, CurrencyAmount amount,
            SupplierPaymentState state, OperationKey captureOperation,
            OperationKey refundOperation) {
        this.orderId = orderId;
        this.amount = amount;
        this.state = state;
        this.captureOperation = captureOperation;
        this.refundOperation = refundOperation;
    }

    public static SupplierPayment open(UUID orderId, CurrencyAmount amount) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(amount, "amount");
        if (amount.units() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        return new SupplierPayment(orderId, amount, null, null, null);
    }

    public SupplierPayment capture(OperationKey operation) {
        Objects.requireNonNull(operation, "operation");
        if (state == SupplierPaymentState.CAPTURED
                && operation.equals(captureOperation)) {
            return this;
        }
        if (state != null) {
            throw new OperationConflictException(operation.value());
        }
        return new SupplierPayment(orderId, amount, SupplierPaymentState.CAPTURED,
                operation, null);
    }

    public SupplierPayment refund(OperationKey operation) {
        Objects.requireNonNull(operation, "operation");
        if (state == SupplierPaymentState.REFUNDED
                && operation.equals(refundOperation)) {
            return this;
        }
        if (state != SupplierPaymentState.CAPTURED) {
            throw new IllegalStateException("Only captured payments can be refunded");
        }
        if (operation.equals(captureOperation)) {
            throw new IllegalStateException("Refund operation must differ from capture operation");
        }
        return new SupplierPayment(orderId, amount, SupplierPaymentState.REFUNDED,
                captureOperation, operation);
    }

    public UUID orderId() { return orderId; }
    public CurrencyAmount amount() { return amount; }
    public SupplierPaymentState state() { return state; }
}
