package vn.restauranttycoon.economy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class EconomyRepository {
    private static final String UNIQUE_VIOLATION = "23505";

    private final DataSource dataSource;

    public EconomyRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public CurrencyAmount balance(UUID accountId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT balance FROM economy_accounts WHERE account_id = ?")) {
            statement.setObject(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new CurrencyAmount(result.getLong(1)) : new CurrencyAmount(0);
            }
        }
    }

    public LedgerResult apply(
            UUID accountId,
            OperationKey operationKey,
            long delta,
            String reason
    ) throws SQLException {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(operationKey, "operationKey");
        if (delta == 0) {
            throw new IllegalArgumentException("Ledger delta cannot be zero");
        }
        if (reason == null || !reason.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Ledger reason must match [A-Z0-9_]{1,64}");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try {
                    LedgerResult result = apply(connection, accountId, operationKey, delta, reason);
                    connection.commit();
                    return result;
                } catch (SQLException exception) {
                    if (!UNIQUE_VIOLATION.equals(exception.getSQLState())) {
                        throw exception;
                    }
                    connection.rollback();
                    return existingAfterConflict(accountId, operationKey, delta, reason, exception);
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public LedgerResult apply(
            Connection connection,
            UUID accountId,
            OperationKey operationKey,
            long delta,
            String reason
    ) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(operationKey, "operationKey");
        if (delta == 0) {
            throw new IllegalArgumentException("Ledger delta cannot be zero");
        }
        if (reason == null || !reason.matches("[A-Z0-9_]{1,64}")) {
            throw new IllegalArgumentException("Ledger reason must match [A-Z0-9_]{1,64}");
        }
        Optional<LedgerResult> duplicate = existing(
                connection, accountId, operationKey, delta, reason);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        ensureAccount(connection, accountId);
        Account account = lockAccount(connection, accountId);
        duplicate = existing(connection, accountId, operationKey, delta, reason);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        long nextBalance = Math.addExact(account.balance(), delta);
        if (nextBalance < 0) {
            throw new InsufficientFundsException(account.balance(), Math.negateExact(delta));
        }
        long nextRevision = Math.addExact(account.revision(), 1);
        updateAccount(connection, accountId, nextBalance, nextRevision);
        insertLedger(connection, accountId, operationKey, delta, nextBalance, nextRevision, reason);
        return new LedgerResult(new CurrencyAmount(nextBalance), nextRevision, false);
    }

    private LedgerResult existingAfterConflict(
            UUID accountId,
            OperationKey operationKey,
            long delta,
            String reason,
            SQLException uniqueViolation
    ) throws SQLException {
        try (Connection retryConnection = dataSource.getConnection()) {
            Optional<LedgerResult> result = existing(
                    retryConnection, accountId, operationKey, delta, reason);
            if (result.isPresent()) {
                retryConnection.rollback();
                return result.get();
            }
        }
        throw uniqueViolation;
    }

    private Optional<LedgerResult> existing(
            Connection connection,
            UUID accountId,
            OperationKey key,
            long delta,
            String reason
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT account_id, delta, reason, balance_after, account_revision "
                        + "FROM economy_ledger WHERE operation_id = ?")) {
            statement.setObject(1, key.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                UUID existingAccount = result.getObject(1, UUID.class);
                long existingDelta = result.getLong(2);
                String existingReason = result.getString(3);
                if (!existingAccount.equals(accountId)
                        || existingDelta != delta
                        || !existingReason.equals(reason)) {
                    throw new OperationKeyConflictException(key);
                }
                return Optional.of(new LedgerResult(
                        new CurrencyAmount(result.getLong(4)), result.getLong(5), true));
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
                    throw new SQLException("Account disappeared while applying ledger operation");
                }
                return new Account(result.getLong(1), result.getLong(2));
            }
        }
    }

    private void updateAccount(
            Connection connection, UUID accountId, long balance, long revision) throws SQLException {
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
            Connection connection,
            UUID accountId,
            OperationKey operationKey,
            long delta,
            long balance,
            long revision,
            String reason
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO economy_ledger "
                        + "(operation_id, account_id, delta, balance_after, account_revision, reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, operationKey.value());
            statement.setObject(2, accountId);
            statement.setLong(3, delta);
            statement.setLong(4, balance);
            statement.setLong(5, revision);
            statement.setString(6, reason);
            statement.executeUpdate();
        }
    }

    private record Account(long balance, long revision) {
    }
}
