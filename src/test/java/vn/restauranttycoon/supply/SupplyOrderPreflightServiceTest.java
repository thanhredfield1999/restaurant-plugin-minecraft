package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.plot.PlotAssignmentRepository;
import vn.restauranttycoon.supplysetup.SupplySetupOwner;
import vn.restauranttycoon.supplysetup.SupplySetupPoint;
import vn.restauranttycoon.supplysetup.SupplySetupPointRepository;
import vn.restauranttycoon.supplysetup.SupplySetupPointType;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyOrderPreflightServiceTest {
    private JdbcDataSource dataSource;
    private UUID playerId;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        playerId = UUID.randomUUID();
        new PlotAssignmentRepository(dataSource).assign("plot_1", playerId, "paper-1");
    }

    @Test
    void reportsMissingSetupBeforeAllowingOrder() throws Exception {
        SupplyOrderPreflight result = new SupplyOrderPreflightService(dataSource)
                .check("plot_1", playerId);

        assertEquals(SupplyOrderPreflight.Status.SETUP_INCOMPLETE, result.status());
        assertTrue(result.details().stream().anyMatch(value -> value.contains("ORDER_DESK")));
        assertTrue(result.details().stream().anyMatch(value -> value.contains("DELIVERY_ENTRY")));
    }

    @Test
    void allowsOwnerWhenCentralAndRestaurantSetupAreComplete() throws Exception {
        SupplySetupPointRepository repository = new SupplySetupPointRepository(dataSource);
        for (SupplySetupPointType type : SupplySetupPointType.values()) {
            SupplySetupOwner owner = type.scope() == vn.restauranttycoon.supplysetup.SupplySetupScope.CENTRAL_SUPPLIER
                    ? SupplySetupOwner.centralSupplier()
                    : SupplySetupOwner.restaurant("plot_1");
            repository.upsert(new SupplySetupPoint(owner, type,
                    new SupplySetupPosition("world", type.ordinal(), 64, 0, 0, 0)));
        }

        assertEquals(SupplyOrderPreflight.Status.READY,
                new SupplyOrderPreflightService(dataSource).check("plot_1", playerId).status());
    }
}