package vn.restauranttycoon.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.EconomyRepository;
import vn.restauranttycoon.economy.InsufficientFundsException;
import vn.restauranttycoon.economy.OperationKey;

class PurchaseRepositoryTest {
    private DataSource dataSource;
    private EconomyRepository economy;
    private PurchaseRepository purchases;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource = database;
        createSchema();
        economy = new EconomyRepository(dataSource);
        purchases = new PurchaseRepository(dataSource);
    }

    @Test
    void atomicallyCommitsChargeUnlockPurchaseAndWorldOperation() throws SQLException {
        UUID accountId = fundedAccount(500);
        PurchaseRequest request = request(accountId, OperationKey.create(), "starter_table", 125);

        PurchaseResult result = purchases.commit(request);

        assertNotNull(result.purchaseId());
        assertNotNull(result.worldOperationId());
        assertEquals(new CurrencyAmount(375), result.balance());
        assertEquals(2, result.accountRevision());
        assertFalse(result.duplicate());
        assertEquals(1, count("unlocks"));
        assertEquals(1, count("purchases"));
        assertEquals(1, count("world_operations"));
        assertEquals(2, count("economy_ledger"));
        assertEquals("REQUESTED", scalarString("SELECT state FROM world_operations"));
    }

    @Test
    void identicalRetryReturnsCommittedIdentifiersWithoutChargingAgain() throws SQLException {
        UUID accountId = fundedAccount(500);
        PurchaseRequest request = request(accountId, OperationKey.create(), "starter_table", 125);

        PurchaseResult first = purchases.commit(request);
        PurchaseResult retry = purchases.commit(request);

        assertTrue(retry.duplicate());
        assertEquals(first.purchaseId(), retry.purchaseId());
        assertEquals(first.worldOperationId(), retry.worldOperationId());
        assertEquals(first.balance(), retry.balance());
        assertEquals(new CurrencyAmount(375), economy.balance(accountId));
        assertEquals(1, count("purchases"));
        assertEquals(1, count("world_operations"));
    }

    @Test
    void operationKeyCannotBeReusedWithChangedSnapshot() throws SQLException {
        UUID accountId = fundedAccount(500);
        OperationKey key = OperationKey.create();
        purchases.commit(request(accountId, key, "starter_table", 125));

        assertThrows(PurchaseConflictException.class,
                () -> purchases.commit(request(accountId, key, "starter_table", 126)));
        assertEquals(new CurrencyAmount(375), economy.balance(accountId));
        assertEquals(1, count("purchases"));
    }

    @Test
    void operationKeyCannotBeReusedFromAnotherLedgerOperation() throws SQLException {
        UUID accountId = UUID.randomUUID();
        OperationKey key = OperationKey.create();
        economy.apply(accountId, key, 500, "TEST_GRANT");
        assignPlot(accountId);

        assertThrows(PurchaseConflictException.class,
                () -> purchases.commit(request(accountId, key, "starter_table", 125)));
        assertEquals(new CurrencyAmount(500), economy.balance(accountId));
        assertEquals(0, count("purchases"));
        assertEquals(0, count("world_operations"));
    }

    @Test
    void secondOperationCannotBuyTheSameUnlock() throws SQLException {
        UUID accountId = fundedAccount(500);
        purchases.commit(request(accountId, OperationKey.create(), "starter_table", 125));

        assertThrows(UnlockAlreadyPurchasedException.class,
                () -> purchases.commit(request(
                        accountId, OperationKey.create(), "starter_table", 125)));
        assertEquals(new CurrencyAmount(375), economy.balance(accountId));
        assertEquals(1, count("purchases"));
        assertEquals(1, count("world_operations"));
    }

    @Test
    void insufficientFundsRollsBackEveryPurchaseRecord() throws SQLException {
        UUID accountId = fundedAccount(50);

        assertThrows(InsufficientFundsException.class,
                () -> purchases.commit(request(
                        accountId, OperationKey.create(), "starter_table", 125)));

        assertEquals(new CurrencyAmount(50), economy.balance(accountId));
        assertEquals(0, count("unlocks"));
        assertEquals(0, count("purchases"));
        assertEquals(0, count("world_operations"));
        assertEquals(1, count("economy_ledger"));
    }

    @Test
    void concurrentIdenticalRetriesCreateOneDurablePurchase() throws Exception {
        UUID accountId = fundedAccount(500);
        PurchaseRequest request = request(
                accountId, OperationKey.create(), "starter_table", 125);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CompletableFuture<?>[] attempts = new CompletableFuture<?>[100];
            for (int index = 0; index < attempts.length; index++) {
                attempts[index] = CompletableFuture.supplyAsync(() -> {
                    try {
                        return purchases.commit(request);
                    } catch (SQLException exception) {
                        throw new IllegalStateException(exception);
                    }
                }, executor);
            }
            CompletableFuture.allOf(attempts).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(new CurrencyAmount(375), economy.balance(accountId));
        assertEquals(1, count("unlocks"));
        assertEquals(1, count("purchases"));
        assertEquals(1, count("world_operations"));
        assertEquals(2, count("economy_ledger"));
    }

    private UUID fundedAccount(long units) throws SQLException {
        UUID accountId = UUID.randomUUID();
        economy.apply(accountId, OperationKey.create(), units, "TEST_GRANT");
        assignPlot(accountId);
        return accountId;
    }

    private void assignPlot(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO plot_assignments
                         (plot_id, account_id, server_id, fence_token,
                          assignment_revision, assigned_at)
                     VALUES ('plot_1', ?, 'server_1', 7, 1, CURRENT_TIMESTAMP)
                     """)) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    private PurchaseRequest request(
            UUID accountId, OperationKey operationKey, String unlockId, long price) {
        return new PurchaseRequest(
                accountId,
                operationKey,
                unlockId,
                1,
                new CurrencyAmount(price),
                "plot_1",
                7,
                2);
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE economy_accounts (
                        account_id UUID PRIMARY KEY,
                        balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
                        revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE economy_ledger (
                        ledger_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        operation_id UUID NOT NULL UNIQUE,
                        account_id UUID NOT NULL REFERENCES economy_accounts(account_id),
                        delta BIGINT NOT NULL CHECK (delta <> 0),
                        balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
                        account_revision BIGINT NOT NULL CHECK (account_revision > 0),
                        reason VARCHAR(64) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE unlocks (
                        account_id UUID NOT NULL REFERENCES economy_accounts(account_id),
                        unlock_id VARCHAR(64) NOT NULL,
                        purchased_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        price_paid BIGINT NOT NULL CHECK (price_paid > 0),
                        definition_version BIGINT NOT NULL CHECK (definition_version > 0),
                        PRIMARY KEY (account_id, unlock_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE plot_assignments (
                        plot_id VARCHAR(64) PRIMARY KEY,
                        account_id UUID UNIQUE REFERENCES economy_accounts(account_id),
                        server_id VARCHAR(64) NOT NULL,
                        fence_token BIGINT NOT NULL CHECK (fence_token > 0),
                        assignment_revision BIGINT NOT NULL CHECK (assignment_revision > 0),
                        assigned_at TIMESTAMP WITH TIME ZONE,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE purchases (
                        purchase_id UUID PRIMARY KEY,
                        operation_id UUID NOT NULL UNIQUE,
                        account_id UUID NOT NULL REFERENCES economy_accounts(account_id),
                        unlock_id VARCHAR(64) NOT NULL,
                        definition_version BIGINT NOT NULL CHECK (definition_version > 0),
                        price BIGINT NOT NULL CHECK (price > 0),
                        state VARCHAR(32) NOT NULL CHECK (state = 'COMMITTED'),
                        plot_id VARCHAR(64) NOT NULL,
                        plot_fence_token BIGINT NOT NULL CHECK (plot_fence_token >= 0),
                        target_stage_revision BIGINT NOT NULL CHECK (target_stage_revision > 0),
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (account_id, unlock_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE world_operations (
                        world_operation_id UUID PRIMARY KEY,
                        purchase_id UUID NOT NULL UNIQUE REFERENCES purchases(purchase_id),
                        operation_type VARCHAR(32) NOT NULL CHECK (operation_type = 'APPLY_STAGE'),
                        plot_id VARCHAR(64) NOT NULL,
                        required_fence_token BIGINT NOT NULL CHECK (required_fence_token >= 0),
                        target_stage_revision BIGINT NOT NULL CHECK (target_stage_revision > 0),
                        state VARCHAR(32) NOT NULL,
                        phase VARCHAR(32) NOT NULL,
                        attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
                        claimed_by_instance VARCHAR(64),
                        claim_expires_at TIMESTAMP WITH TIME ZONE,
                        last_error VARCHAR(1024),
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    @Test
    void stalePlotFenceRollsBackPurchase() throws SQLException {
        UUID accountId = fundedAccount(500);

        assertThrows(PlotAssignmentMismatchException.class,
                () -> purchases.commit(new PurchaseRequest(
                        accountId,
                        OperationKey.create(),
                        "starter_table",
                        1,
                        new CurrencyAmount(125),
                        "plot_1",
                        8,
                        2)));

        assertEquals(new CurrencyAmount(500), economy.balance(accountId));
        assertEquals(0, count("purchases"));
        assertEquals(0, count("world_operations"));
    }

    private int count(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private String scalarString(String query) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            result.next();
            return result.getString(1);
        }
    }
}
