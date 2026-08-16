package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplyFulfillmentRepositoryTest {
    private DataSource dataSource;
    private SupplyFulfillmentRepository repository;
    private final UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private final UUID restaurantId = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private final UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000043");

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
        dataSource = database;
        repository = new SupplyFulfillmentRepository(dataSource);
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) "
                        + "VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')")) {
            statement.setObject(1, orderId);
            statement.setObject(2, UUID.randomUUID());
            statement.setObject(3, restaurantId);
            statement.setObject(4, playerId);
            statement.executeUpdate();
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)")) {
            statement.setObject(1, orderId);
            statement.setObject(2, UUID.randomUUID());
            statement.executeUpdate();
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) "
                        + "VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)")) {
            statement.setObject(1, orderId);
            statement.executeUpdate();
        }
    }

    @Test
    void createsOneShipmentAndPackageForPaidOrderAndRetriesIdempotently() throws SQLException {
        SupplyFulfillmentRecord first = repository.createForPaidOrder(orderId, restaurantId);
        SupplyFulfillmentRecord retry = repository.createForPaidOrder(orderId, restaurantId);

        assertNotNull(first.shipmentId());
        assertNotNull(first.packageId());
        assertEquals(first, retry);
        assertEquals(SupplyShipmentState.CREATED, first.shipmentState());
        assertEquals(SupplyPackageState.IN_TRANSIT, first.packageState());
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT quantity FROM supply_package_lines WHERE package_id = ?")) {
            statement.setObject(1, first.packageId());
            try (var result = statement.executeQuery()) {
                assertEquals(true, result.next());
                assertEquals(4, result.getInt(1));
            }
        }
    }
}
