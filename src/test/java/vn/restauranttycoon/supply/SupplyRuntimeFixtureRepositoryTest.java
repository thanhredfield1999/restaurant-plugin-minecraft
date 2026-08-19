package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplyRuntimeFixtureRepositoryTest {
    private DataSource dataSource;
    private SupplyRuntimeFixtureRepository fixtureRepository;
    private UUID fixtureId;
    private UUID restaurantId;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
        dataSource = database;
        fixtureRepository = new SupplyRuntimeFixtureRepository(database);
        fixtureId = UUID.randomUUID();
        restaurantId = UUID.randomUUID();
        playerId = UUID.randomUUID();
    }

    @Test
    void seedCreatesPaidShipmentPinnedProjectionAndIsIdempotent() throws Exception {
        SupplyRuntimeFixture first = fixtureRepository.seed(fixtureId, restaurantId, playerId);
        SupplyRuntimeFixture retry = fixtureRepository.seed(fixtureId, restaurantId, playerId);

        assertEquals(first, retry);
        assertNotNull(first.shipmentId());
        assertNotNull(first.packageId());
        assertTrue(fixtureRepository.hasRecoverableProjection(first.shipmentId()));
        assertEquals(1, count("supply_orders", "operation_id", fixtureId));
        assertEquals(1, count("supply_shipments", "shipment_id", first.shipmentId()));
        assertEquals(1, count("supply_packages", "package_id", first.packageId()));
    }

    @Test
    void cleanupIsIdempotentAndRemovesFixtureGraph() throws Exception {
        SupplyRuntimeFixture seeded = fixtureRepository.seed(fixtureId, restaurantId, playerId);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO warehouse_stock_operations (operation_id, package_id, restaurant_id) VALUES (?, ?, ?)")) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, seeded.packageId());
            statement.setObject(3, restaurantId);
            statement.executeUpdate();
        }

        assertTrue(fixtureRepository.cleanup(fixtureId));
        assertFalse(fixtureRepository.cleanup(fixtureId));
        assertEquals(0, count("supply_orders", "operation_id", fixtureId));
        assertEquals(0, count("supply_shipments", "shipment_id", seeded.shipmentId()));
    }

    @Test
    void sameFixtureWithDifferentOwnerFailsClosed() throws Exception {
        fixtureRepository.seed(fixtureId, restaurantId, playerId);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> fixtureRepository.seed(fixtureId, UUID.randomUUID(), playerId));
    }

    private int count(String table, String column, UUID value) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            statement.setObject(1, value);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
