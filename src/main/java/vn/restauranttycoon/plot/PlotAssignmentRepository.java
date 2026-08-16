package vn.restauranttycoon.plot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class PlotAssignmentRepository {
    private final DataSource dataSource;

    public PlotAssignmentRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public PlotAssignment assign(String plotId, UUID accountId, String serverId) throws SQLException {
        validateIdentifier(plotId, "plotId");
        Objects.requireNonNull(accountId, "accountId");
        validateIdentifier(serverId, "serverId");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureAccount(connection, accountId);
                Optional<PlotAssignment> current = lock(connection, plotId);
                if (current.isPresent() && current.get().accountId().isPresent()) {
                    if (current.get().accountId().equals(Optional.of(accountId))
                            && current.get().serverId().equals(serverId)) {
                        connection.rollback();
                        return current.get();
                    }
                    throw new PlotAssignmentConflictException("Plot is already assigned: " + plotId);
                }
                if (accountHasPlot(connection, accountId)) {
                    throw new PlotAssignmentConflictException(
                            "Account already has an assigned plot: " + accountId);
                }
                if (current.isEmpty()) {
                    insert(connection, plotId, accountId, serverId);
                } else {
                    reassign(connection, current.get(), accountId, serverId);
                }
                PlotAssignment assigned = lock(connection, plotId).orElseThrow();
                connection.commit();
                return assigned;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public PlotAssignment allocate(
            UUID accountId,
            String serverId,
            List<String> configuredPlotIds
    ) throws SQLException {
        Objects.requireNonNull(accountId, "accountId");
        validateIdentifier(serverId, "serverId");
        Objects.requireNonNull(configuredPlotIds, "configuredPlotIds");
        if (configuredPlotIds.isEmpty()) {
            throw new IllegalArgumentException("configuredPlotIds must not be empty");
        }
        configuredPlotIds.forEach(plotId -> validateIdentifier(plotId, "plotId"));
        if (configuredPlotIds.stream().distinct().count() != configuredPlotIds.size()) {
            throw new IllegalArgumentException("configuredPlotIds must not contain duplicates");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                ensureAccount(connection, accountId);
                lockAccount(connection, accountId);
                materializeConfiguredPlots(connection, serverId, configuredPlotIds);
                Optional<PlotAssignment> existing = findByAccount(connection, accountId);
                if (existing.isPresent()) {
                    connection.rollback();
                    return existing.get();
                }
                for (String plotId : configuredPlotIds) {
                    Optional<PlotAssignment> current = lock(connection, plotId);
                    if (current.isPresent() && current.get().accountId().isPresent()) {
                        continue;
                    }
                    if (current.isEmpty()) {
                        insert(connection, plotId, accountId, serverId);
                    } else {
                        reassign(connection, current.get(), accountId, serverId);
                    }
                    PlotAssignment assigned = lock(connection, plotId).orElseThrow();
                    connection.commit();
                    return assigned;
                }
                throw new PlotAssignmentConflictException("No configured plot is available");
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void lockAccount(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM economy_accounts WHERE account_id = ? FOR UPDATE")) {
            statement.setObject(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Economy account disappeared during plot allocation");
                }
            }
        }
    }

    private void materializeConfiguredPlots(
            Connection connection,
            String serverId,
            List<String> configuredPlotIds
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO plot_assignments
                    (plot_id, account_id, server_id, fence_token,
                     assignment_revision, assigned_at)
                VALUES (?, NULL, ?, 1, 1, NULL)
                ON CONFLICT DO NOTHING
                """)) {
            for (String plotId : configuredPlotIds) {
                statement.setString(1, plotId);
                statement.setString(2, serverId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Optional<PlotAssignment> findByAccount(Connection connection, UUID accountId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT plot_id, account_id, server_id, fence_token, assignment_revision
                FROM plot_assignments WHERE account_id = ?
                """)) {
            statement.setObject(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public PlotAssignment release(String plotId, UUID accountId, long expectedFenceToken)
            throws SQLException {
        validateIdentifier(plotId, "plotId");
        Objects.requireNonNull(accountId, "accountId");
        if (expectedFenceToken < 1) {
            throw new IllegalArgumentException("expectedFenceToken must be positive");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                PlotAssignment current = lock(connection, plotId)
                        .orElseThrow(() -> new PlotAssignmentConflictException(
                                "Plot assignment does not exist: " + plotId));
                if (!current.accountId().equals(Optional.of(accountId))
                        || current.fenceToken() != expectedFenceToken) {
                    throw new PlotAssignmentConflictException(
                            "Plot assignment ownership or fence token changed: " + plotId);
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE plot_assignments
                        SET account_id = NULL, fence_token = fence_token + 1,
                            assignment_revision = assignment_revision + 1,
                            assigned_at = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE plot_id = ? AND account_id = ? AND fence_token = ?
                        """)) {
                    statement.setString(1, plotId);
                    statement.setObject(2, accountId);
                    statement.setLong(3, expectedFenceToken);
                    if (statement.executeUpdate() != 1) {
                        throw new PlotAssignmentConflictException(
                                "Plot assignment changed during release: " + plotId);
                    }
                }
                PlotAssignment released = lock(connection, plotId).orElseThrow();
                connection.commit();
                return released;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Optional<PlotAssignment> find(String plotId) throws SQLException {
        validateIdentifier(plotId, "plotId");
        try (Connection connection = dataSource.getConnection()) {
            return read(connection, plotId, false);
        }
    }

    private Optional<PlotAssignment> lock(Connection connection, String plotId) throws SQLException {
        return read(connection, plotId, true);
    }

    private Optional<PlotAssignment> read(Connection connection, String plotId, boolean forUpdate)
            throws SQLException {
        String sql = "SELECT plot_id, account_id, server_id, fence_token, assignment_revision "
                + "FROM plot_assignments WHERE plot_id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, plotId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(result));
            }
        }
    }

    private boolean accountHasPlot(Connection connection, UUID accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM plot_assignments WHERE account_id = ?")) {
            statement.setObject(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
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

    private void insert(Connection connection, String plotId, UUID accountId, String serverId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO plot_assignments
                    (plot_id, account_id, server_id, fence_token, assignment_revision, assigned_at)
                VALUES (?, ?, ?, 1, 1, CURRENT_TIMESTAMP)
                """)) {
            statement.setString(1, plotId);
            statement.setObject(2, accountId);
            statement.setString(3, serverId);
            statement.executeUpdate();
        }
    }

    private void reassign(
            Connection connection, PlotAssignment current, UUID accountId, String serverId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE plot_assignments
                SET account_id = ?, server_id = ?, fence_token = fence_token + 1,
                    assignment_revision = assignment_revision + 1,
                    assigned_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE plot_id = ? AND account_id IS NULL AND fence_token = ?
                """)) {
            statement.setObject(1, accountId);
            statement.setString(2, serverId);
            statement.setString(3, current.plotId());
            statement.setLong(4, current.fenceToken());
            if (statement.executeUpdate() != 1) {
                throw new PlotAssignmentConflictException(
                        "Plot assignment changed during assign: " + current.plotId());
            }
        }
    }

    private PlotAssignment map(ResultSet result) throws SQLException {
        return new PlotAssignment(
                result.getString(1),
                Optional.ofNullable(result.getObject(2, UUID.class)),
                result.getString(3),
                result.getLong(4),
                result.getLong(5));
    }

    private static void validateIdentifier(String value, String name) {
        if (value == null || !value.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException(name + " must be a safe identifier of at most 64 characters");
        }
    }
}
