package vn.restauranttycoon.economy;

public final class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(long available, long requested) {
        super("Insufficient funds: available=" + available + ", requested=" + requested);
    }
}
