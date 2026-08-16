package vn.restauranttycoon.economy;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import vn.restauranttycoon.persistence.DatabaseManager;

public final class EconomyService {
    private final DatabaseManager database;

    public EconomyService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<CurrencyAmount> balance(UUID accountId) {
        return supply(() -> repository().balance(accountId));
    }

    public CompletableFuture<LedgerResult> grant(
            UUID accountId, CurrencyAmount amount, OperationKey operationKey) {
        if (amount.units() == 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Grant amount must be positive"));
        }
        return supply(() -> repository().apply(
                accountId, operationKey, amount.units(), "ADMIN_GRANT"));
    }

    public CompletableFuture<LedgerResult> debit(
            UUID accountId,
            CurrencyAmount amount,
            OperationKey operationKey,
            String reason
    ) {
        if (amount.units() == 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Debit amount must be positive"));
        }
        return supply(() -> repository().apply(
                accountId, operationKey, Math.negateExact(amount.units()), reason));
    }

    private EconomyRepository repository() {
        return new EconomyRepository(database.requireDataSource());
    }

    private <T> CompletableFuture<T> supply(SqlSupplier<T> action) {
        Executor executor = database.executor();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (SQLException exception) {
                throw new EconomyStorageException(exception);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }
}
