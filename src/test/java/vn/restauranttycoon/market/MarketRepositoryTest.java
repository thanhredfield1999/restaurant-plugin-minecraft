package vn.restauranttycoon.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.OperationKey;

class MarketRepositoryTest {
    private DataSource dataSource;
    private MarketRepository repository;
    private final Instant now = Instant.parse("2026-08-19T00:00:00Z");

    @BeforeEach
    void setUp() {
        JdbcDataSource h2 = new JdbcDataSource();
        h2.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(h2).locations("classpath:db/migration").load().migrate();
        dataSource = h2;
        repository = new MarketRepository(dataSource);
    }

    @Test
    void createsOneOpenCycleAndSeedsPrices() throws Exception {
        MarketCycle first = repository.ensureOpenCycle(Map.of("tomato", 25L), now, Duration.ofMinutes(10));
        MarketCycle second = repository.ensureOpenCycle(Map.of("tomato", 99L), now.plusSeconds(1), Duration.ofMinutes(10));

        assertEquals(first.cycleId(), second.cycleId());
        assertEquals(25L, repository.findPrice("tomato", now.plusSeconds(1)).unitPrice());
    }

    @Test
    void concurrentWorkersConvergeOnOneOpenCycle() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        try {
            var tasks = java.util.stream.IntStream.range(0, 4)
                    .<Callable<MarketCycle>>mapToObj(i -> () ->
                            new MarketRepository(dataSource).ensureOpenCycle(
                                    Map.of("tomato", 25L), now, Duration.ofMinutes(10)))
                    .toList();
            var results = pool.invokeAll(tasks);
            var ids = results.stream().map(future -> {
                try { return future.get().cycleId(); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }).collect(java.util.stream.Collectors.toSet());
            assertEquals(1, ids.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void purchaseEventUpdatesAggregateExactlyOnceOnRetry() throws Exception {
        repository.ensureOpenCycle(Map.of("tomato", 25L), now, Duration.ofMinutes(10));
        OperationKey operation = OperationKey.create();

        MarketEventResult first = repository.recordPurchase(operation, "tomato", 3, now.plusSeconds(1));
        MarketEventResult retry = repository.recordPurchase(operation, "tomato", 3, now.plusSeconds(2));

        assertEquals(false, first.duplicate());
        assertEquals(true, retry.duplicate());
        assertEquals(first.eventId(), retry.eventId());
        assertEquals(3L, repository.findPrice("tomato", now.plusSeconds(2)).quantityDemanded());
    }

    @Test
    void reusingOperationWithDifferentPayloadIsRejected() throws Exception {
        repository.ensureOpenCycle(Map.of("tomato", 25L), now, Duration.ofMinutes(10));
        OperationKey operation = OperationKey.create();
        repository.recordPurchase(operation, "tomato", 3, now.plusSeconds(1));

        assertThrows(MarketOperationConflictException.class,
                () -> repository.recordPurchase(operation, "tomato", 4, now.plusSeconds(2)));
    }

    @Test
    void expiredCycleHasNoCurrentPrice() throws Exception {
        repository.ensureOpenCycle(Map.of("tomato", 25L), now, Duration.ofMinutes(10));

        assertNotNull(repository.findPrice("tomato", now.plusSeconds(599)));
        org.junit.jupiter.api.Assertions.assertNull(repository.findPrice("tomato", now.plusSeconds(600)));
    }
}
