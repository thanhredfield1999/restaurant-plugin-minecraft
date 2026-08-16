package vn.restauranttycoon.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals("12", requiredVersion.get(null));
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

        assertEquals(12, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertEquals("12", flyway.info().current().getVersion().getVersion());

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
                                            'supply_package_lines')
                     """)) {
            int tableCount = 0;
            while (result.next()) {
                tableCount++;
            }
            assertEquals(19, tableCount);
        }
        assertTrue(flyway.validateWithResult().validationSuccessful);
    }
}
