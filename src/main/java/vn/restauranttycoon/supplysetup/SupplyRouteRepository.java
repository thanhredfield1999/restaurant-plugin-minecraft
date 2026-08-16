package vn.restauranttycoon.supplysetup;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

public final class SupplyRouteRepository {
    private final DataSource dataSource;

    public SupplyRouteRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void replaceAll(SupplyRoute route) throws SQLException {
        Objects.requireNonNull(route, "route");
        SupplySetupOwner owner = route.owner();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement("""
                        DELETE FROM supply_route_waypoints
                        WHERE setup_scope = ? AND owner_id = ?
                        """)) {
                    delete.setString(1, owner.scope().name());
                    delete.setString(2, owner.ownerId());
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO supply_route_waypoints
                            (setup_scope, owner_id, sequence_no, waypoint_name,
                             world_name, x, y, z, yaw, pitch)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    for (SupplyRouteWaypoint waypoint : route.waypoints()) {
                        bind(insert, waypoint);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public SupplyRoute findAll(SupplySetupOwner owner) throws SQLException {
        Objects.requireNonNull(owner, "owner");
        if (owner.scope() != SupplySetupScope.RESTAURANT) {
            throw new IllegalArgumentException("routes can only belong to a restaurant");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT owner_id, sequence_no, waypoint_name, world_name,
                            x, y, z, yaw, pitch
                     FROM supply_route_waypoints
                     WHERE setup_scope = ? AND owner_id = ?
                     ORDER BY sequence_no
                     """)) {
            statement.setString(1, owner.scope().name());
            statement.setString(2, owner.ownerId());
            try (ResultSet result = statement.executeQuery()) {
                List<SupplyRouteWaypoint> waypoints = new ArrayList<>();
                while (result.next()) {
                    waypoints.add(map(owner, result));
                }
                return new SupplyRoute(owner, waypoints);
            }
        }
    }

    private static void bind(PreparedStatement statement, SupplyRouteWaypoint waypoint)
            throws SQLException {
        SupplySetupPosition position = waypoint.position();
        statement.setString(1, waypoint.owner().scope().name());
        statement.setString(2, waypoint.owner().ownerId());
        statement.setInt(3, waypoint.sequence());
        statement.setString(4, waypoint.name());
        statement.setString(5, position.worldName());
        statement.setDouble(6, position.x());
        statement.setDouble(7, position.y());
        statement.setDouble(8, position.z());
        statement.setFloat(9, position.yaw());
        statement.setFloat(10, position.pitch());
    }

    private static SupplyRouteWaypoint map(SupplySetupOwner owner, ResultSet result)
            throws SQLException {
        return new SupplyRouteWaypoint(
                owner,
                result.getInt("sequence_no"),
                result.getString("waypoint_name"),
                new SupplySetupPosition(
                        result.getString("world_name"),
                        result.getDouble("x"),
                        result.getDouble("y"),
                        result.getDouble("z"),
                        result.getFloat("yaw"),
                        result.getFloat("pitch")));
    }
}
