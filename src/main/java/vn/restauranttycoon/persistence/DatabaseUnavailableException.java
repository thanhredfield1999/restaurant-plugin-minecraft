package vn.restauranttycoon.persistence;

public final class DatabaseUnavailableException extends IllegalStateException {
    public DatabaseUnavailableException(DatabaseState state) {
        super("Database is not ready: " + state);
    }
}
