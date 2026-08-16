package vn.restauranttycoon.supplysetup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public final class SupplySetupPointRepository {
    private final DataSource dataSource;

    public SupplySetupPointRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void upsert(SupplySetupPoint point) throws SQLException {
        Objects.requireNonNull(point, "point");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!update(connection, point)) {
                    insert(connection, point);
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Optional<SupplySetupPoint> find(
            SupplySetupOwner owner,
            SupplySetupPointType type
    ) throws SQLException {
        requireCompatible(owner, type);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT setup_scope, owner_id, point_type, world_name, x, y, z, yaw, pitch
                     FROM supply_setup_points
                     WHERE setup_scope = ? AND owner_id = ? AND point_type = ?
                     """)) {
            bindIdentity(statement, owner, type);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public List<SupplySetupPoint> findAll(SupplySetupOwner owner) throws SQLException {
        Objects.requireNonNull(owner, "owner");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT setup_scope, owner_id, point_type, world_name, x, y, z, yaw, pitch
                     FROM supply_setup_points
                     WHERE setup_scope = ? AND owner_id = ?
                     ORDER BY point_type
                     """)) {
            statement.setString(1, owner.scope().name());
            statement.setString(2, owner.ownerId());
            try (ResultSet result = statement.executeQuery()) {
                List<SupplySetupPoint> points = new ArrayList<>();
                while (result.next()) {
                    points.add(map(result));
                }
                return List.copyOf(points);
            }
        }
    }

    public boolean delete(SupplySetupOwner owner, SupplySetupPointType type) throws SQLException {
        requireCompatible(owner, type);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    DELETE FROM supply_setup_points
                    WHERE setup_scope = ? AND owner_id = ? AND point_type = ?
                    """)) {
                bindIdentity(statement, owner, type);
                boolean deleted = statement.executeUpdate() == 1;
                connection.commit();
                return deleted;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static boolean update(Connection connection, SupplySetupPoint point) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE supply_setup_points
                SET world_name = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE setup_scope = ? AND owner_id = ? AND point_type = ?
                """)) {
            SupplySetupPosition position = point.position();
            statement.setString(1, position.worldName());
            statement.setDouble(2, position.x());
            statement.setDouble(3, position.y());
            statement.setDouble(4, position.z());
            statement.setFloat(5, position.yaw());
            statement.setFloat(6, position.pitch());
            statement.setString(7, point.owner().scope().name());
            statement.setString(8, point.owner().ownerId());
            statement.setString(9, point.type().name());
            return statement.executeUpdate() == 1;
        }
    }

    private static void insert(Connection connection, SupplySetupPoint point) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO supply_setup_points
                    (setup_scope, owner_id, point_type, world_name, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindIdentity(statement, point.owner(), point.type());
            SupplySetupPosition position = point.position();
            statement.setString(4, position.worldName());
            statement.setDouble(5, position.x());
            statement.setDouble(6, position.y());
            statement.setDouble(7, position.z());
            statement.setFloat(8, position.yaw());
            statement.setFloat(9, position.pitch());
            statement.executeUpdate();
        }
    }

    private static void bindIdentity(
            PreparedStatement statement,
            SupplySetupOwner owner,
            SupplySetupPointType type
    ) throws SQLException {
        requireCompatible(owner, type);
        statement.setString(1, owner.scope().name());
        statement.setString(2, owner.ownerId());
        statement.setString(3, type.name());
    }

    private static void requireCompatible(SupplySetupOwner owner, SupplySetupPointType type) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(type, "type");
        if (owner.scope() != type.scope()) {
            throw new IllegalArgumentException(
                    "point type " + type + " cannot belong to scope " + owner.scope());
        }
    }

    private static SupplySetupPoint map(ResultSet result) throws SQLException {
        SupplySetupOwner owner = new SupplySetupOwner(
                SupplySetupScope.valueOf(result.getString(1)),
                result.getString(2));
        SupplySetupPointType type = SupplySetupPointType.valueOf(result.getString(3));
        SupplySetupPosition position = new SupplySetupPosition(
                result.getString(4),
                result.getDouble(5),
                result.getDouble(6),
                result.getDouble(7),
                result.getFloat(8),
                result.getFloat(9));
        return new SupplySetupPoint(owner, type, position);
    }
}
