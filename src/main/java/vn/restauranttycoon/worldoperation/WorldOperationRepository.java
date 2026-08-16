package vn.restauranttycoon.worldoperation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class WorldOperationRepository {
    private final DataSource dataSource;

    public WorldOperationRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public UUID requestInitialStage(
            UUID accountId,
            String plotId,
            long requiredFenceToken,
            long targetStageRevision
    ) throws SQLException {
        Objects.requireNonNull(accountId, "accountId");
        if (plotId == null || !plotId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("plotId must be a safe identifier of at most 64 characters");
        }
        if (requiredFenceToken < 1) {
            throw new IllegalArgumentException("requiredFenceToken must be positive");
        }
        if (targetStageRevision < 1) {
            throw new IllegalArgumentException("targetStageRevision must be positive");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockOnboardingFence(connection, accountId, plotId, requiredFenceToken);
                Optional<UUID> existing = findOnboardingOperation(
                        connection, plotId, requiredFenceToken, targetStageRevision);
                if (existing.isPresent()) {
                    connection.rollback();
                    return existing.get();
                }
                UUID operationId = UUID.randomUUID();
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO world_operations
                            (world_operation_id, purchase_id, operation_type, plot_id,
                             required_fence_token, target_stage_revision, state, phase,
                             source_account_id, operation_source)
                        VALUES (?, NULL, 'APPLY_STAGE', ?, ?, ?, 'REQUESTED', 'PENDING',
                                ?, 'ONBOARDING')
                        """)) {
                    statement.setObject(1, operationId);
                    statement.setString(2, plotId);
                    statement.setLong(3, requiredFenceToken);
                    statement.setLong(4, targetStageRevision);
                    statement.setObject(5, accountId);
                    statement.executeUpdate();
                }
                connection.commit();
                return operationId;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void lockOnboardingFence(
            Connection connection,
            UUID accountId,
            String plotId,
            long requiredFenceToken
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM plot_assignments
                WHERE plot_id = ? AND account_id = ? AND fence_token = ?
                FOR UPDATE
                """)) {
            statement.setString(1, plotId);
            statement.setObject(2, accountId);
            statement.setLong(3, requiredFenceToken);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new StalePlotFenceException(
                            "Plot assignment ownership or fence changed: " + plotId);
                }
            }
        }
    }

    private Optional<UUID> findOnboardingOperation(
            Connection connection,
            String plotId,
            long requiredFenceToken,
            long targetStageRevision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT world_operation_id FROM world_operations
                WHERE plot_id = ? AND required_fence_token = ?
                  AND target_stage_revision = ? AND operation_source = 'ONBOARDING'
                """)) {
            statement.setString(1, plotId);
            statement.setLong(2, requiredFenceToken);
            statement.setLong(3, targetStageRevision);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(result.getObject(1, UUID.class))
                        : Optional.empty();
            }
        }
    }

    public Optional<WorldOperationClaim> claimNext(String instanceId, Duration lease)
            throws SQLException {
        validateInstanceId(instanceId);
        long leaseSeconds = validateLease(lease);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<UUID> candidate = findClaimable(connection);
                if (candidate.isEmpty()) {
                    connection.rollback();
                    return Optional.empty();
                }
                UUID claimToken = UUID.randomUUID();
                OffsetDateTime claimExpiresAt = databaseNow(connection).plusSeconds(leaseSeconds);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE world_operations
                        SET state = 'APPLYING', phase = 'CLAIMED', attempt_count = attempt_count + 1,
                            claimed_by_instance = ?, claim_token = ?,
                            claim_expires_at = ?,
                            last_error = NULL, updated_at = CURRENT_TIMESTAMP
                        WHERE world_operation_id = ?
                          AND state IN ('REQUESTED', 'REPAIR_REQUIRED', 'APPLYING')
                          AND (state <> 'APPLYING' OR claim_expires_at <= CURRENT_TIMESTAMP)
                        """)) {
                    statement.setString(1, instanceId);
                    statement.setObject(2, claimToken);
                    statement.setObject(3, claimExpiresAt);
                    statement.setObject(4, candidate.get());
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return Optional.empty();
                    }
                }
                WorldOperationClaim claim = loadClaim(connection, candidate.get(), claimToken);
                connection.commit();
                return Optional.of(claim);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public WorldOperationClaim renew(WorldOperationClaim claim, Duration lease) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        long leaseSeconds = validateLease(lease);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            OffsetDateTime claimExpiresAt = databaseNow(connection).plusSeconds(leaseSeconds);
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE world_operations
                    SET claim_expires_at = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE world_operation_id = ? AND state = 'APPLYING'
                      AND claimed_by_instance = ? AND claim_token = ?
                      AND claim_expires_at > CURRENT_TIMESTAMP
                    """)) {
                statement.setObject(1, claimExpiresAt);
                statement.setObject(2, claim.worldOperationId());
                statement.setString(3, claim.instanceId());
                statement.setObject(4, claim.claimToken());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    throw new StaleWorldOperationClaimException(claim);
                }
                WorldOperationClaim renewed = loadClaim(
                        connection, claim.worldOperationId(), claim.claimToken());
                connection.commit();
                return renewed;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void markApplied(WorldOperationClaim claim) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                lockCurrentFence(connection, claim);
                upsertProjectionState(connection, claim);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE world_operations
                        SET state = 'APPLIED', phase = 'COMPLETE',
                            claimed_by_instance = NULL, claim_token = NULL,
                            claim_expires_at = NULL, last_error = NULL,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE world_operation_id = ? AND state = 'APPLYING'
                          AND claimed_by_instance = ? AND claim_token = ?
                          AND claim_expires_at > CURRENT_TIMESTAMP
                        """)) {
                    bindClaim(statement, claim);
                    if (statement.executeUpdate() != 1) {
                        throw new StaleWorldOperationClaimException(claim);
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public void assertCurrentFence(WorldOperationClaim claim) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        assertActiveClaim(claim);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1
                     FROM world_operations w
                     LEFT JOIN purchases p ON p.purchase_id = w.purchase_id
                     JOIN plot_assignments a ON a.plot_id = w.plot_id
                     WHERE w.world_operation_id = ?
                       AND a.account_id = COALESCE(p.account_id, w.source_account_id)
                       AND a.fence_token = w.required_fence_token
                     """)) {
            statement.setObject(1, claim.worldOperationId());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new StalePlotFenceException(claim);
                }
            }
        }
    }

    public void markRepairRequired(WorldOperationClaim claim, String error) throws SQLException {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        transition(claim, "REPAIR_REQUIRED", "PENDING", truncate(error, 1024), false);
    }

    private void transition(
            WorldOperationClaim claim,
            String state,
            String phase,
            String error,
            boolean requireCurrentFence
    ) throws SQLException {
        Objects.requireNonNull(claim, "claim");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            String fencePredicate = requireCurrentFence ? """
                      AND EXISTS (
                          SELECT 1
                          FROM plot_assignments a
                          LEFT JOIN purchases p ON p.purchase_id = world_operations.purchase_id
                          WHERE a.plot_id = world_operations.plot_id
                            AND a.account_id = COALESCE(p.account_id, world_operations.source_account_id)
                            AND a.fence_token = world_operations.required_fence_token
                      )
                    """ : "";
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE world_operations
                    SET state = ?, phase = ?, claimed_by_instance = NULL, claim_token = NULL,
                        claim_expires_at = NULL, last_error = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE world_operation_id = ? AND state = 'APPLYING'
                      AND claimed_by_instance = ? AND claim_token = ?
                      AND claim_expires_at > CURRENT_TIMESTAMP
                    """ + fencePredicate)) {
                statement.setString(1, state);
                statement.setString(2, phase);
                statement.setString(3, error);
                statement.setObject(4, claim.worldOperationId());
                statement.setString(5, claim.instanceId());
                statement.setObject(6, claim.claimToken());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    if (requireCurrentFence) {
                        assertCurrentFence(claim);
                    }
                    throw new StaleWorldOperationClaimException(claim);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void assertActiveClaim(WorldOperationClaim claim) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM world_operations
                     WHERE world_operation_id = ? AND state = 'APPLYING'
                       AND claimed_by_instance = ? AND claim_token = ?
                       AND claim_expires_at > CURRENT_TIMESTAMP
                     """)) {
            statement.setObject(1, claim.worldOperationId());
            statement.setString(2, claim.instanceId());
            statement.setObject(3, claim.claimToken());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new StaleWorldOperationClaimException(claim);
                }
            }
        }
    }

    private void lockCurrentFence(Connection connection, WorldOperationClaim claim)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1
                FROM world_operations w
                JOIN plot_assignments a ON a.plot_id = w.plot_id
                WHERE w.world_operation_id = ? AND w.state = 'APPLYING'
                  AND w.claimed_by_instance = ? AND w.claim_token = ?
                  AND w.claim_expires_at > CURRENT_TIMESTAMP
                  AND a.account_id = COALESCE(
                      (SELECT p.account_id FROM purchases p
                       WHERE p.purchase_id = w.purchase_id),
                      w.source_account_id)
                  AND a.fence_token = w.required_fence_token
                FOR UPDATE
                """)) {
            bindClaim(statement, claim);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    assertActiveClaim(claim);
                    throw new StalePlotFenceException(claim);
                }
            }
        }
    }

    private void upsertProjectionState(Connection connection, WorldOperationClaim claim)
            throws SQLException {
        Optional<ProjectionState> current = lockProjectionState(connection, claim.plotId());
        if (current.isPresent()
                && current.get().fenceToken() == claim.requiredFenceToken()
                && current.get().stageRevision() > claim.targetStageRevision()) {
            throw new StaleWorldOperationClaimException(claim);
        }
        String sql = current.isEmpty()
                ? """
                  INSERT INTO plot_projection_state
                      (plot_id, fence_token, stage_revision, last_world_operation_id)
                  VALUES (?, ?, ?, ?)
                  """
                : """
                  UPDATE plot_projection_state
                  SET fence_token = ?, stage_revision = ?, last_world_operation_id = ?,
                      updated_at = CURRENT_TIMESTAMP
                  WHERE plot_id = ?
                  """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (current.isEmpty()) {
                statement.setString(1, claim.plotId());
                statement.setLong(2, claim.requiredFenceToken());
                statement.setLong(3, claim.targetStageRevision());
                statement.setObject(4, claim.worldOperationId());
            } else {
                statement.setLong(1, claim.requiredFenceToken());
                statement.setLong(2, claim.targetStageRevision());
                statement.setObject(3, claim.worldOperationId());
                statement.setString(4, claim.plotId());
            }
            if (statement.executeUpdate() != 1) {
                throw new StaleWorldOperationClaimException(claim);
            }
        }
    }

    private Optional<ProjectionState> lockProjectionState(Connection connection, String plotId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT fence_token, stage_revision
                FROM plot_projection_state
                WHERE plot_id = ?
                FOR UPDATE
                """)) {
            statement.setString(1, plotId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(new ProjectionState(result.getLong(1), result.getLong(2)))
                        : Optional.empty();
            }
        }
    }

    private void bindClaim(PreparedStatement statement, WorldOperationClaim claim)
            throws SQLException {
        statement.setObject(1, claim.worldOperationId());
        statement.setString(2, claim.instanceId());
        statement.setObject(3, claim.claimToken());
    }

    private Optional<UUID> findClaimable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT world_operation_id
                FROM world_operations
                WHERE state IN ('REQUESTED', 'REPAIR_REQUIRED')
                   OR (state = 'APPLYING' AND claim_expires_at <= CURRENT_TIMESTAMP)
                ORDER BY created_at, world_operation_id
                FETCH FIRST 1 ROW ONLY
                FOR UPDATE SKIP LOCKED
                """);
             ResultSet result = statement.executeQuery()) {
            return result.next()
                    ? Optional.of(result.getObject(1, UUID.class))
                    : Optional.empty();
        }
    }

    private WorldOperationClaim loadClaim(Connection connection, UUID operationId, UUID claimToken)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT world_operation_id, purchase_id, plot_id, required_fence_token,
                       target_stage_revision, attempt_count, claimed_by_instance,
                       claim_token, claim_expires_at
                FROM world_operations
                WHERE world_operation_id = ? AND claim_token = ?
                """)) {
            statement.setObject(1, operationId);
            statement.setObject(2, claimToken);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Claim disappeared while loading world operation");
                }
                return new WorldOperationClaim(
                        result.getObject(1, UUID.class),
                        result.getObject(2, UUID.class),
                        result.getString(3),
                        result.getLong(4),
                        result.getLong(5),
                        result.getInt(6),
                        result.getString(7),
                        result.getObject(8, UUID.class),
                        result.getObject(9, OffsetDateTime.class).toInstant());
            }
        }
    }

    private OffsetDateTime databaseNow(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("Database did not return CURRENT_TIMESTAMP");
            }
            return result.getObject(1, OffsetDateTime.class);
        }
    }

    private static void validateInstanceId(String instanceId) {
        if (instanceId == null || !instanceId.matches("[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("instanceId must be a safe identifier of at most 64 characters");
        }
    }

    private static long validateLease(Duration lease) {
        Objects.requireNonNull(lease, "lease");
        long seconds = lease.getSeconds();
        if (seconds < 1 || seconds > 300 || lease.getNano() != 0) {
            throw new IllegalArgumentException("lease must be a whole number of seconds from 1 to 300");
        }
        return seconds;
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private record ProjectionState(long fenceToken, long stageRevision) {
    }
}
