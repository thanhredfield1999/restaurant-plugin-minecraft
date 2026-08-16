package vn.restauranttycoon.economy;

public final class EconomyStorageException extends RuntimeException {
    public EconomyStorageException(Throwable cause) {
        super("Economy storage operation failed", cause);
    }
}
