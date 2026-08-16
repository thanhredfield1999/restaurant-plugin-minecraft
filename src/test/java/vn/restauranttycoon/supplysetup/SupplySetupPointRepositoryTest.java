package vn.restauranttycoon.supplysetup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplySetupPointRepositoryTest {
    private SupplySetupPointRepository repository;

    @BeforeEach
    void migrateDatabase() {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        DataSource dataSource = database;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .load().migrate();
        repository = new SupplySetupPointRepository(dataSource);
    }

    @Test
    void upsertReloadAndDeletePreserveExactPoint() throws Exception {
        SupplySetupOwner owner = SupplySetupOwner.restaurant("plot_1");
        SupplySetupPoint original = new SupplySetupPoint(
                owner,
                SupplySetupPointType.DELIVERY_STOP,
                new SupplySetupPosition("world", 1.25, 65.0, -2.5, 45.0f, 10.0f));
        SupplySetupPoint replacement = new SupplySetupPoint(
                owner,
                SupplySetupPointType.DELIVERY_STOP,
                new SupplySetupPosition("world_nether", 7.0, 80.5, 3.0, -90.0f, -20.0f));

        repository.upsert(original);
        repository.upsert(replacement);

        assertEquals(replacement, repository.find(owner, SupplySetupPointType.DELIVERY_STOP).orElseThrow());
        assertEquals(1, repository.findAll(owner).size());
        assertTrue(repository.delete(owner, SupplySetupPointType.DELIVERY_STOP));
        assertTrue(repository.find(owner, SupplySetupPointType.DELIVERY_STOP).isEmpty());
    }
}
