package vn.restauranttycoon.purchase;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import vn.restauranttycoon.economy.EconomyStorageException;
import vn.restauranttycoon.persistence.DatabaseManager;

public final class PurchaseService {
    private final DatabaseManager database;

    public PurchaseService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<PurchaseResult> commit(PurchaseRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new PurchaseRepository(database.requireDataSource()).commit(request);
            } catch (SQLException exception) {
                throw new EconomyStorageException(exception);
            }
        }, database.executor());
    }
}
