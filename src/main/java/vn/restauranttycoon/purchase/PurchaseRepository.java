package vn.restauranttycoon.purchase;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.InsufficientFundsException;

public final class PurchaseRepository {
    private final DataSource dataSource;

    public PurchaseRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public PurchaseResult commit(PurchaseRequest request) throws SQLException {
        Objects.requireNonNull(request, "request");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PurchaseResult> duplicate = existing(connection, request);
                if (duplicate.isPresent()) {
                    connection.rollback();
                    return duplicate.get();
                }

                ensureAccount(connection, request.accountId());
                Account account = lockAccount(connection, request.accountId());
                duplicate = existing(connection, request);
                if (duplicate.isPresent()) {
                    connection.rollback();
                    return duplicate.get();
                }
                if (hasLedgerOperation(connection, request.operationKey().value())) {
                    throw new PurchaseConflictException(request);
                }
                if (hasUnlock(connection, request.accountId(), request.unlockId())) {
                    throw new UnlockAlreadyPurchasedException(request.unlockId());
                }
                if (!ownsPlotFence(connection, request)) {
                    throw new PlotAssignmentMismatchException(request);
                }

                long nextBalance = account.balance() - request.price().units();
                if (nextBalance < 0) {
                    throw new InsufficientFundsException(account.balance(), request.price().units());
                }
                long nextRevision = Math.addExact(account.revision(), 1);
                UUID purchaseId = UUID.randomUUID();
                UUID worldOperationId = UUID.randomUUID();

                updateAccount(connection, request.accountId(), nextBalance, nextRevision);
                insertLedger(connection, request, nextBalance, nextRevision);
                insertUnlock(connection, request);
                insertPurchase(connection, request, purchaseId);
                insertWorldOperation(connection, request, purchaseId, worldOperationId);
                connection.commit();
                return new PurchaseResult(
                        purchaseId,
                        worldOperationId,
                        new CurrencyAmount(nextBalance),
                        nextRevision,
                        false);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private Optional<PurchaseResult> existing(Connection connection, PurchaseRequest request)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.purchase_id, p.account_id, p.unlock_id, p.definition_version,
                       p.price, p.plot_id, p.plot_fence_token, p.target_stage_revision,
                       w.world_operation_id, l.balance_after, l.account_revision
                FROM purchases p
                JOIN world_operations w ON w.purchase_id = p.purchase_id
                JOIN economy_ledger l ON l.operation_id = p.operation_id
                WHERE p.operation_id = ?
                """)) {
            statement.setObject(1, request.operationKey().value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                if (!result.getObject(2, UUID.class).equals(request.accountId())
                        || !result.getString(3).equals(request.unlockId())
                        || result.getLong(4) != request.definitionVersion()
                        || result.getLong(5) != request.price().units()
                        || !result.getString(6).equals(request.plotId())
                        || result.getLong(7) != request.plotFenceToken()
                        || result.getLong(8) != request.targetStageRevision()) {
                    throw new PurchaseConflictException(request);
                }
                return Optional.of(new PurchaseResult(
                        result.getObject(1, UUID.class),
                        result.getObject(9, UUID.class),
                        new CurrencyAmount(result.getLong(10)),
                        result.getLong(11),
                        true));
            }
        }
    }

    private void ensureAccount(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO economy_accounts (account_id) VALUES (?) ON CONFLICT DO NOTHING")) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    private Account lockAccount(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance, revision FROM economy_accounts WHERE account_id = ? FOR UPDATE")) {
            statement.setObject(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Account disappeared while committing purchase");
                }
                return new Account(result.getLong(1), result.getLong(2));
            }
        }
    }

    private boolean hasUnlock(Connection connection, UUID accountId, String unlockId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM unlocks WHERE account_id = ? AND unlock_id = ?")) {
            statement.setObject(1, accountId);
            statement.setString(2, unlockId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean hasLedgerOperation(Connection connection, UUID operationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM economy_ledger WHERE operation_id = ?")) {
            statement.setObject(1, operationId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean ownsPlotFence(Connection connection, PurchaseRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM plot_assignments
                WHERE plot_id = ? AND account_id = ? AND fence_token = ?
                """)) {
            statement.setString(1, request.plotId());
            statement.setObject(2, request.accountId());
            statement.setLong(3, request.plotFenceToken());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void updateAccount(Connection connection, UUID accountId, long balance, long revision)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE economy_accounts SET balance = ?, revision = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE account_id = ?")) {
            statement.setLong(1, balance);
            statement.setLong(2, revision);
            statement.setObject(3, accountId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Account update affected an unexpected number of rows");
            }
        }
    }

    private void insertLedger(
            Connection connection, PurchaseRequest request, long balance, long revision)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO economy_ledger
                    (operation_id, account_id, delta, balance_after, account_revision, reason)
                VALUES (?, ?, ?, ?, ?, 'PURCHASE')
                """)) {
            statement.setObject(1, request.operationKey().value());
            statement.setObject(2, request.accountId());
            statement.setLong(3, Math.negateExact(request.price().units()));
            statement.setLong(4, balance);
            statement.setLong(5, revision);
            statement.executeUpdate();
        }
    }

    private void insertUnlock(Connection connection, PurchaseRequest request) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO unlocks
                    (account_id, unlock_id, price_paid, definition_version)
                VALUES (?, ?, ?, ?)
                """)) {
            statement.setObject(1, request.accountId());
            statement.setString(2, request.unlockId());
            statement.setLong(3, request.price().units());
            statement.setLong(4, request.definitionVersion());
            statement.executeUpdate();
        }
    }

    private void insertPurchase(Connection connection, PurchaseRequest request, UUID purchaseId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO purchases
                    (purchase_id, operation_id, account_id, unlock_id, definition_version,
                     price, state, plot_id, plot_fence_token, target_stage_revision)
                VALUES (?, ?, ?, ?, ?, ?, 'COMMITTED', ?, ?, ?)
                """)) {
            statement.setObject(1, purchaseId);
            statement.setObject(2, request.operationKey().value());
            statement.setObject(3, request.accountId());
            statement.setString(4, request.unlockId());
            statement.setLong(5, request.definitionVersion());
            statement.setLong(6, request.price().units());
            statement.setString(7, request.plotId());
            statement.setLong(8, request.plotFenceToken());
            statement.setLong(9, request.targetStageRevision());
            statement.executeUpdate();
        }
    }

    private void insertWorldOperation(
            Connection connection,
            PurchaseRequest request,
            UUID purchaseId,
            UUID worldOperationId
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO world_operations
                    (world_operation_id, purchase_id, operation_type, plot_id,
                     required_fence_token, target_stage_revision, state, phase)
                VALUES (?, ?, 'APPLY_STAGE', ?, ?, ?, 'REQUESTED', 'PENDING')
                """)) {
            statement.setObject(1, worldOperationId);
            statement.setObject(2, purchaseId);
            statement.setString(3, request.plotId());
            statement.setLong(4, request.plotFenceToken());
            statement.setLong(5, request.targetStageRevision());
            statement.executeUpdate();
        }
    }

    private record Account(long balance, long revision) {
    }
}
