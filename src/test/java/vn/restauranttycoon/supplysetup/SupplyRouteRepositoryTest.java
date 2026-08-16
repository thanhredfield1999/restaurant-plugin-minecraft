package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class SupplyRouteRepositoryTest {
    private SupplyRouteRepository repository;

    @BeforeEach
    void migrateDatabase() {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        DataSource dataSource = database;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .load().migrate();
        repository = new SupplyRouteRepository(dataSource);
    }

    @Test
    void replaceAllRemovesOldWaypointsAndPreservesNewOrder() throws Exception {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplyRoute first = new SupplyRoute(List.of(
                waypoint(owner, 1, "Cổng vào"),
                waypoint(owner, 2, "Điểm nhận"),
                waypoint(owner, 3, "Cổng ra")));
        SupplyRoute replacement = new SupplyRoute(List.of(
                waypoint(owner, 1, "Cổng vào mới"),
                waypoint(owner, 2, "Kho")));

        repository.replaceAll(first);
        repository.replaceAll(replacement);

        assertEquals(replacement.waypoints(), repository.findAll(owner).waypoints());
    }

    @Test
    void replacingWithEmptyRouteDeletesEverySavedWaypoint() throws Exception {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        repository.replaceAll(new SupplyRoute(owner, List.of(waypoint(owner, 1, "Cổng vào"))));

        repository.replaceAll(new SupplyRoute(owner, List.of()));

        assertEquals(List.of(), repository.findAll(owner).waypoints());
    }

    private static SupplyRouteWaypoint waypoint(SupplySetupOwner owner, int sequence, String name) {
        return new SupplyRouteWaypoint(owner, sequence, name,
                new SupplySetupPosition("world", sequence, 64, sequence, 0, 0));
    }
}
