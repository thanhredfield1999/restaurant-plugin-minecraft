package vn.restauranttycoon.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class DatabaseMigrationTest {
    @Test
    void databaseStartupRequiresLatestBundledSchemaVersion() throws Exception {
        Field requiredVersion = DatabaseManager.class.getDeclaredField("REQUIRED_SCHEMA_VERSION");
        requiredVersion.setAccessible(true);

        assertEquals("17", requiredVersion.get(null));
    }

    @Test
    void freshMigrationAndRerunReachVersionEight() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        assertEquals(17, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("17", flyway.info().current().getVersion().getVersion());

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT table_name
                     FROM information_schema.tables
                     WHERE table_schema = 'public'
                       AND table_name IN ('economy_accounts', 'economy_ledger', 'unlocks',
                                           'purchases', 'world_operations', 'plot_assignments',
                                            'plot_projection_state', 'dish_entitlements',
                                            'dish_entitlement_operations', 'supply_setup_points',
                                            'supply_route_waypoints', 'supply_orders',
                                            'supply_order_lines', 'supply_payments',
                                            'supply_shipments', 'supply_packages',
                                            'warehouse_stock', 'warehouse_stock_operations',
                                            'supply_package_lines', 'supply_shipment_runtime',
                                            'market_cycles', 'market_prices', 'market_demand_events')
                     """)) {
            int tableCount = 0;
            while (result.next()) {
                tableCount++;
            }
            assertEquals(23, tableCount);
        }
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }

    @Test
    void upgradeFromVersionTwelveMarksExistingShipmentForManualRecovery() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("12")
                .load()
                .migrate();

        UUID shipmentId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO supply_orders
                        (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state)
                    VALUES (RANDOM_UUID(), RANDOM_UUID(), RANDOM_UUID(), RANDOM_UUID(), 1, 1, 'SUBMITTED')
                    """);
            try (var shipment = connection.prepareStatement("""
                    INSERT INTO supply_shipments (shipment_id, order_id, restaurant_id, state)
                    SELECT ?, order_id, restaurant_id, 'IN_TRANSIT'
                    FROM supply_orders FETCH FIRST 1 ROW ONLY
                    """)) {
                shipment.setObject(1, shipmentId);
                shipment.executeUpdate();
            }
        }

        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        try (Connection connection = dataSource.getConnection();
             var query = connection.prepareStatement("""
                     SELECT checkpoint_stage, recovery_outcome, journey_snapshot
                     FROM supply_shipment_runtime WHERE shipment_id = ?
                     """)) {
            query.setObject(1, shipmentId);
            try (ResultSet result = query.executeQuery()) {
                assertTrue(result.next());
                assertEquals("PENDING_MANUAL", result.getString(1));
                assertEquals("PENDING_MANUAL", result.getString(2));
                assertEquals("{\"schema\":\"legacy-v12-no-runtime-snapshot\"}", result.getString(3));
            }
        }
    }

    @Test
    void marketSchemaRejectsPartialPriceSnapshot() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        UUID cycleId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             var cycle = connection.prepareStatement("""
                     INSERT INTO market_cycles
                         (cycle_id, cycle_number, starts_at, ends_at, state)
                     VALUES (?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '10' MINUTE, 'OPEN')
                     """)) {
            cycle.setObject(1, cycleId);
            cycle.executeUpdate();
            try (var price = connection.prepareStatement("""
                    INSERT INTO supply_order_lines
                        (order_id, sku, display_name, unit, quantity, unit_price, market_cycle_id)
                    VALUES (RANDOM_UUID(), 'tomato', 'Tomato', 'PIECE', 1, 25, ?)
                    """)) {
                price.setObject(1, cycleId);
                assertThrows(java.sql.SQLException.class, price::executeUpdate);
            }
        }
    }

    @Test
    void runtimeSnapshotRejectsInvalidUnloadDeadlineState() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        UUID shipmentId = insertShipment(dataSource);

        try (Connection connection = dataSource.getConnection();
             var snapshot = connection.prepareStatement("""
                     INSERT INTO supply_shipment_runtime
                         (shipment_id, revision, checkpoint_stage, journey_snapshot_version,
                          journey_snapshot, recovery_outcome)
                     VALUES (?, 1, 'UNLOAD_POINT', 1, '{}', 'NONE')
                     """)) {
            snapshot.setObject(1, shipmentId);
            assertThrows(java.sql.SQLException.class, snapshot::executeUpdate);
        }
    }

    private static UUID insertShipment(JdbcDataSource dataSource) throws Exception {
        UUID shipmentId = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO supply_orders
                        (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state)
                    VALUES (RANDOM_UUID(), RANDOM_UUID(), RANDOM_UUID(), RANDOM_UUID(), 1, 1, 'SUBMITTED')
                    """);
            try (var shipment = connection.prepareStatement("""
                    INSERT INTO supply_shipments (shipment_id, order_id, restaurant_id, state)
                    SELECT ?, order_id, restaurant_id, 'IN_TRANSIT'
                    FROM supply_orders FETCH FIRST 1 ROW ONLY
                    """)) {
                shipment.setObject(1, shipmentId);
                shipment.executeUpdate();
            }
        }
        return shipmentId;
    }
}
