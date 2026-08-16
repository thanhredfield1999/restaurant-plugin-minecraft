package vn.restauranttycoon.dish;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import vn.restauranttycoon.economy.OperationKey;

public final class DishEntitlementRepository {
    private static final String UNIQUE_VIOLATION = "23505";
    private static final int MAX_CLAIMED_QUERY_LIMIT = 100;

    private final DataSource dataSource;

    public DishEntitlementRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public DishEntitlement issue(
            UUID entitlementId,
            UUID orderId,
            UUID restaurantId,
            String recipeId,
            long recipeVersion
    ) throws SQLException {
        DishEntitlement requested = new DishEntitlement(
                entitlementId, orderId, restaurantId, recipeId, recipeVersion,
                DishEntitlementState.AVAILABLE, Optional.empty(), 0);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<DishEntitlement> existing = findByOrder(connection, orderId, true);
                if (existing.isPresent()) {
                    connection.rollback();
                    return requireSameIssue(existing.get(), requested);
                }
                try {
                    insertEntitlement(connection, requested);
                } catch (SQLException exception) {
                    if (!UNIQUE_VIOLATION.equals(exception.getSQLState())) {
                        throw exception;
                    }
                    connection.rollback();
                    return existingIssueAfterConflict(requested, exception);
                }
                connection.commit();
                return requested;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public DishEntitlementResult claim(
            UUID entitlementId, UUID playerId, OperationKey operationKey) throws SQLException {
        return transition(entitlementId, playerId, operationKey, "CLAIM",
                DishEntitlementState.AVAILABLE, DishEntitlementState.CLAIMED);
    }

    public DishEntitlementResult consume(
            UUID entitlementId, UUID playerId, OperationKey operationKey) throws SQLException {
        return transition(entitlementId, playerId, operationKey, "CONSUME",
                DishEntitlementState.CLAIMED, DishEntitlementState.CONSUMED);
    }

    public Optional<DishEntitlement> find(UUID entitlementId) throws SQLException {
        Objects.requireNonNull(entitlementId, "entitlementId");
        try (Connection connection = dataSource.getConnection()) {
            return findById(connection, entitlementId, false);
        }
    }

    public List<DishEntitlement> findClaimedByHolder(UUID holderId, int limit)
            throws SQLException {
        Objects.requireNonNull(holderId, "holderId");
        if (limit <= 0 || limit > MAX_CLAIMED_QUERY_LIMIT) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and " + MAX_CLAIMED_QUERY_LIMIT);
        }
        try (Connection connection = dataSource.getConnection()) {
            return findClaimedByHolder(connection, holderId, limit);
        }
    }

    private List<DishEntitlement> findClaimedByHolder(
            Connection connection, UUID holderId, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                     SELECT entitlement_id, order_id, restaurant_id, recipe_id,
                            recipe_version, state, holder_id, state_revision
                     FROM dish_entitlements
                     WHERE holder_id = ? AND state = 'CLAIMED'
                     ORDER BY entitlement_id
                     LIMIT ?
                     """)) {
            statement.setObject(1, holderId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<DishEntitlement> found = new ArrayList<>();
                while (result.next()) {
                    found.add(readEntitlement(result));
                }
                return List.copyOf(found);
            }
        }
    }

    public Map<UUID, DishEntitlement> findByIds(Set<UUID> entitlementIds)
            throws SQLException {
        Objects.requireNonNull(entitlementIds, "entitlementIds");
        if (entitlementIds.size() > MAX_CLAIMED_QUERY_LIMIT) {
            throw new IllegalArgumentException(
                    "At most " + MAX_CLAIMED_QUERY_LIMIT + " entitlement IDs may be queried");
        }
        if (entitlementIds.isEmpty()) {
            return Map.of();
        }
        if (entitlementIds.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("entitlementIds cannot contain null");
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(
                entitlementIds.size(), "?"));
        String sql = "SELECT entitlement_id, order_id, restaurant_id, recipe_id, "
                + "recipe_version, state, holder_id, state_revision FROM dish_entitlements "
                + "WHERE entitlement_id IN (" + placeholders + ") ORDER BY entitlement_id";
        try (Connection connection = dataSource.getConnection()) {
            return findByIds(connection, entitlementIds, sql);
        }
    }

    public DishEntitlementReconciliationSnapshot loadReconciliationSnapshot(
            UUID holderId, int projectionLimit, Set<UUID> observedEntitlementIds)
            throws SQLException {
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(observedEntitlementIds, "observedEntitlementIds");
        if (projectionLimit <= 0 || projectionLimit > MAX_CLAIMED_QUERY_LIMIT) {
            throw new IllegalArgumentException(
                    "projectionLimit must be between 1 and " + MAX_CLAIMED_QUERY_LIMIT);
        }
        if (observedEntitlementIds.size() > MAX_CLAIMED_QUERY_LIMIT) {
            throw new IllegalArgumentException(
                    "At most " + MAX_CLAIMED_QUERY_LIMIT + " observed entitlement IDs are allowed");
        }
        if (observedEntitlementIds.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("observedEntitlementIds cannot contain null");
        }

        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            try {
                DishEntitlementReconciliationSnapshot snapshot = loadReconciliationSnapshot(
                        connection, holderId, projectionLimit, observedEntitlementIds);
                connection.commit();
                return snapshot;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private DishEntitlementReconciliationSnapshot loadReconciliationSnapshot(
            Connection connection,
            UUID holderId,
            int projectionLimit,
            Set<UUID> observedEntitlementIds
    ) throws SQLException {
        String projection = """
                SELECT 'PROJECTION' AS snapshot_scope, entitlement_id, order_id,
                       restaurant_id, recipe_id, recipe_version, state, holder_id,
                       state_revision
                FROM (
                    SELECT entitlement_id, order_id, restaurant_id, recipe_id,
                           recipe_version, state, holder_id, state_revision
                    FROM dish_entitlements
                    WHERE holder_id = ? AND state = 'CLAIMED'
                    ORDER BY entitlement_id
                    LIMIT ?
                ) claimed_projection
                """;
        String sql = observedEntitlementIds.isEmpty()
                ? projection
                : projection + """
                        UNION ALL
                        SELECT 'TOKEN' AS snapshot_scope, entitlement_id, order_id,
                               restaurant_id, recipe_id, recipe_version, state, holder_id,
                               state_revision
                        FROM dish_entitlements
                        WHERE entitlement_id IN (
                        """ + placeholders(observedEntitlementIds.size()) + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, holderId);
            statement.setInt(2, projectionLimit);
            int parameter = 3;
            for (UUID entitlementId : observedEntitlementIds) {
                statement.setObject(parameter++, entitlementId);
            }
            try (ResultSet result = statement.executeQuery()) {
                Map<UUID, DishEntitlement> projectedById = new LinkedHashMap<>();
                Map<UUID, DishEntitlement> validation = new LinkedHashMap<>();
                while (result.next()) {
                    DishEntitlement entitlement = readEntitlement(result);
                    Map<UUID, DishEntitlement> destination = "PROJECTION".equals(
                            result.getString("snapshot_scope"))
                            ? projectedById : validation;
                    destination.put(entitlement.entitlementId(), entitlement);
                }
                return new DishEntitlementReconciliationSnapshot(projectedById, validation);
            }
        }
    }

    private String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private String entitlementIdsSql(int count) {
        String placeholders = String.join(", ", java.util.Collections.nCopies(count, "?"));
        return "SELECT entitlement_id, order_id, restaurant_id, recipe_id, "
                + "recipe_version, state, holder_id, state_revision FROM dish_entitlements "
                + "WHERE entitlement_id IN (" + placeholders + ") ORDER BY entitlement_id";
    }

    private Map<UUID, DishEntitlement> findByIds(
            Connection connection, Set<UUID> entitlementIds, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (UUID entitlementId : entitlementIds) {
                statement.setObject(parameter++, entitlementId);
            }
            try (ResultSet result = statement.executeQuery()) {
                Map<UUID, DishEntitlement> found = new LinkedHashMap<>();
                while (result.next()) {
                    DishEntitlement entitlement = readEntitlement(result);
                    found.put(entitlement.entitlementId(), entitlement);
                }
                return Map.copyOf(found);
            }
        }
    }

    private DishEntitlementResult transition(
            UUID entitlementId,
            UUID playerId,
            OperationKey operationKey,
            String operationType,
            DishEntitlementState expected,
            DishEntitlementState target
    ) throws SQLException {
        Objects.requireNonNull(entitlementId, "entitlementId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(operationKey, "operationKey");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<DishEntitlementResult> duplicate = existingOperation(
                        connection, entitlementId, playerId, operationKey, operationType);
                if (duplicate.isPresent()) {
                    connection.rollback();
                    return duplicate.get();
                }
                DishEntitlement current = findById(connection, entitlementId, true)
                        .orElseThrow(() -> new DishEntitlementConflictException(
                                "Dish entitlement does not exist: " + entitlementId));
                duplicate = existingOperation(
                        connection, entitlementId, playerId, operationKey, operationType);
                if (duplicate.isPresent()) {
                    connection.rollback();
                    return duplicate.get();
                }
                if (current.state() != expected
                        || (expected == DishEntitlementState.CLAIMED
                        && !current.holderId().equals(Optional.of(playerId)))) {
                    throw new DishEntitlementConflictException(
                            "Dish entitlement cannot transition from " + current.state()
                                    + " using " + operationType);
                }
                long revision = Math.addExact(current.stateRevision(), 1);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE dish_entitlements
                        SET state = ?, holder_id = ?, state_revision = ?,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE entitlement_id = ? AND state_revision = ?
                        """)) {
                    statement.setString(1, target.name());
                    statement.setObject(2, playerId);
                    statement.setLong(3, revision);
                    statement.setObject(4, entitlementId);
                    statement.setLong(5, current.stateRevision());
                    if (statement.executeUpdate() != 1) {
                        throw new DishEntitlementConflictException(
                                "Dish entitlement changed during transition");
                    }
                }
                try {
                    insertOperation(connection, operationKey, entitlementId, operationType,
                            playerId, target, revision);
                } catch (SQLException exception) {
                    if (!UNIQUE_VIOLATION.equals(exception.getSQLState())) {
                        throw exception;
                    }
                    connection.rollback();
                    return existingOperationAfterConflict(
                            entitlementId, playerId, operationKey, operationType, exception);
                }
                connection.commit();
                return new DishEntitlementResult(new DishEntitlement(
                        current.entitlementId(), current.orderId(), current.restaurantId(),
                        current.recipeId(), current.recipeVersion(), target,
                        Optional.of(playerId), revision), false);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void insertEntitlement(Connection connection, DishEntitlement entitlement)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO dish_entitlements
                    (entitlement_id, order_id, restaurant_id, recipe_id,
                     recipe_version, state)
                VALUES (?, ?, ?, ?, ?, 'AVAILABLE')
                """)) {
            statement.setObject(1, entitlement.entitlementId());
            statement.setObject(2, entitlement.orderId());
            statement.setObject(3, entitlement.restaurantId());
            statement.setString(4, entitlement.recipeId());
            statement.setLong(5, entitlement.recipeVersion());
            statement.executeUpdate();
        }
    }

    private DishEntitlement existingIssueAfterConflict(
            DishEntitlement requested, SQLException uniqueViolation) throws SQLException {
        try (Connection retryConnection = dataSource.getConnection()) {
            Optional<DishEntitlement> existing = findByOrder(
                    retryConnection, requested.orderId(), false);
            if (existing.isPresent()) {
                return requireSameIssue(existing.get(), requested);
            }
            if (findById(retryConnection, requested.entitlementId(), false).isPresent()) {
                throw new DishEntitlementConflictException(
                        "Entitlement ID already belongs to a different order");
            }
        }
        throw uniqueViolation;
    }

    private DishEntitlementResult existingOperationAfterConflict(
            UUID entitlementId,
            UUID playerId,
            OperationKey operationKey,
            String operationType,
            SQLException uniqueViolation
    ) throws SQLException {
        try (Connection retryConnection = dataSource.getConnection()) {
            Optional<DishEntitlementResult> existing = existingOperation(
                    retryConnection, entitlementId, playerId, operationKey, operationType);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        throw uniqueViolation;
    }

    private Optional<DishEntitlementResult> existingOperation(
            Connection connection,
            UUID entitlementId,
            UUID playerId,
            OperationKey operationKey,
            String operationType
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT o.entitlement_id, o.operation_type, o.player_id,
                       e.order_id, e.restaurant_id, e.recipe_id, e.recipe_version,
                       o.resulting_state, e.holder_id, o.resulting_revision
                FROM dish_entitlement_operations o
                JOIN dish_entitlements e ON e.entitlement_id = o.entitlement_id
                WHERE o.operation_id = ?
                """)) {
            statement.setObject(1, operationKey.value());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                if (!result.getObject(1, UUID.class).equals(entitlementId)
                        || !result.getString(2).equals(operationType)
                        || !result.getObject(3, UUID.class).equals(playerId)) {
                    throw new DishEntitlementConflictException(
                            "Dish operation key was reused with different inputs");
                }
                return Optional.of(new DishEntitlementResult(new DishEntitlement(
                        entitlementId,
                        result.getObject(4, UUID.class),
                        result.getObject(5, UUID.class),
                        result.getString(6),
                        result.getLong(7),
                        DishEntitlementState.valueOf(result.getString(8)),
                        Optional.ofNullable(result.getObject(9, UUID.class)),
                        result.getLong(10)), true));
            }
        }
    }

    private void insertOperation(
            Connection connection,
            OperationKey operationKey,
            UUID entitlementId,
            String operationType,
            UUID playerId,
            DishEntitlementState state,
            long revision
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO dish_entitlement_operations
                    (operation_id, entitlement_id, operation_type, player_id,
                     resulting_state, resulting_revision)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setObject(1, operationKey.value());
            statement.setObject(2, entitlementId);
            statement.setString(3, operationType);
            statement.setObject(4, playerId);
            statement.setString(5, state.name());
            statement.setLong(6, revision);
            statement.executeUpdate();
        }
    }

    private Optional<DishEntitlement> findById(
            Connection connection, UUID entitlementId, boolean lock) throws SQLException {
        return findOne(connection,
                "SELECT entitlement_id, order_id, restaurant_id, recipe_id, recipe_version, "
                        + "state, holder_id, state_revision FROM dish_entitlements "
                        + "WHERE entitlement_id = ?" + (lock ? " FOR UPDATE" : ""),
                entitlementId);
    }

    private Optional<DishEntitlement> findByOrder(
            Connection connection, UUID orderId, boolean lock) throws SQLException {
        return findOne(connection,
                "SELECT entitlement_id, order_id, restaurant_id, recipe_id, recipe_version, "
                        + "state, holder_id, state_revision FROM dish_entitlements "
                        + "WHERE order_id = ?" + (lock ? " FOR UPDATE" : ""),
                orderId);
    }

    private Optional<DishEntitlement> findOne(
            Connection connection, String sql, UUID id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(readEntitlement(result));
            }
        }
    }

    private DishEntitlement readEntitlement(ResultSet result) throws SQLException {
        return new DishEntitlement(
                result.getObject("entitlement_id", UUID.class),
                result.getObject("order_id", UUID.class),
                result.getObject("restaurant_id", UUID.class),
                result.getString("recipe_id"),
                result.getLong("recipe_version"),
                DishEntitlementState.valueOf(result.getString("state")),
                Optional.ofNullable(result.getObject("holder_id", UUID.class)),
                result.getLong("state_revision"));
    }

    private DishEntitlement requireSameIssue(
            DishEntitlement existing, DishEntitlement requested) {
        if (!existing.entitlementId().equals(requested.entitlementId())
                || !existing.restaurantId().equals(requested.restaurantId())
                || !existing.recipeId().equals(requested.recipeId())
                || existing.recipeVersion() != requested.recipeVersion()) {
            throw new DishEntitlementConflictException(
                    "Order already has a different dish entitlement");
        }
        return existing;
    }
}
