package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.CurrencyAmount;
import vn.restauranttycoon.economy.EconomyRepository;
import vn.restauranttycoon.economy.OperationKey;
import vn.restauranttycoon.plot.PlotAssignmentRepository;
import vn.restauranttycoon.supplysetup.SupplySetupOwner;
import vn.restauranttycoon.supplysetup.SupplySetupPoint;
import vn.restauranttycoon.supplysetup.SupplySetupPointRepository;
import vn.restauranttycoon.supplysetup.SupplySetupPointType;
import vn.restauranttycoon.supplysetup.SupplySetupPosition;

class SupplyOrderRepositoryTest {
    private DataSource dataSource;
    private EconomyRepository economy;
    private UUID playerId;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
        dataSource = database;
        economy = new EconomyRepository(dataSource);
        playerId = UUID.randomUUID();
        economy.apply(playerId, OperationKey.create(), 500, "TEST_GRANT");
    }

    @Test
    void capturesPaymentAndPersistsSubmittedOrderExactlyOnce() throws SQLException {
        UUID orderId = UUID.randomUUID();
        UUID submitOperationId = UUID.randomUUID();
        OperationKey captureOperation = OperationKey.create();
        SupplierOrder order = order(submitOperationId);
        SupplyOrderRepository repository = new SupplyOrderRepository(dataSource);

        SupplyOrderReceipt first = repository.capture(orderId, order, captureOperation);
        SupplyOrderReceipt retry = repository.capture(orderId, order, captureOperation);

        assertFalse(first.duplicate());
        assertTrue(retry.duplicate());
        assertEquals(first.orderId(), retry.orderId());
        assertEquals(new CurrencyAmount(400), first.balance());
        assertEquals(new CurrencyAmount(400), economy.balance(playerId));
        assertEquals(1, count("supply_orders"));
        assertEquals(2, count("supply_order_lines"));
        assertEquals(1, count("supply_payments"));
        assertEquals(1, count("supply_shipments"));
        assertEquals(1, count("supply_packages"));
        assertEquals(2, count("supply_package_lines"));
        assertEquals(1, countLedger(captureOperation.value()));
    }

    @Test
    void rollsBackDebitAndOrderWhenPaymentPersistenceFails() throws SQLException {
        UUID orderId = UUID.randomUUID();
        OperationKey captureOperation = OperationKey.create();
        SupplierOrder order = order(UUID.randomUUID());
        SupplyOrderRepository repository = new SupplyOrderRepository(dataSource);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE supply_payments");
        }

        assertThrows(SQLException.class, () -> repository.capture(orderId, order, captureOperation));

        assertEquals(new CurrencyAmount(500), economy.balance(playerId));
        assertEquals(0, count("supply_orders"));
        assertEquals(0, countLedger(captureOperation.value()));
    }

    @Test
    void rejectsCaptureWhenPlotIsNotOwnedByOrderingPlayer() throws SQLException {
        String plotId = "plot_1";
        UUID otherPlayer = UUID.randomUUID();
        new PlotAssignmentRepository(dataSource).assign(plotId, otherPlayer, "paper-1");
        SupplierOrder order = order(UUID.randomUUID());
        SupplyOrderRepository repository = new SupplyOrderRepository(dataSource);

        assertThrows(SupplyOrderAuthorizationException.class, () -> repository.captureAuthorized(
                plotId,
                UUID.randomUUID(),
                order,
                OperationKey.create(),
                catalog()));

        assertEquals(new CurrencyAmount(500), economy.balance(playerId));
        assertEquals(0, count("supply_orders"));
    }

    @Test
    void capturesWhenPlotOwnershipMatchesOrderingPlayer() throws SQLException {
        String plotId = "plot_1";
        new PlotAssignmentRepository(dataSource).assign(plotId, playerId, "paper-1");
        completeSetup(plotId);
        SupplierOrder order = order(UUID.randomUUID());
        SupplyOrderRepository repository = new SupplyOrderRepository(dataSource);

        SupplyOrderReceipt receipt = repository.captureAuthorized(
                plotId,
                UUID.randomUUID(),
                order,
                OperationKey.create(),
                catalog());

        assertFalse(receipt.duplicate());
        assertEquals(new CurrencyAmount(400), receipt.balance());
        assertEquals(1, count("supply_orders"));
    }

    @Test
    void rejectsCaptureWhenSetupBecameIncompleteAfterReadyPreflight() throws SQLException {
        String plotId = "plot_1";
        new PlotAssignmentRepository(dataSource).assign(plotId, playerId, "paper-1");
        SupplySetupPointRepository setup = completeSetup(plotId);
        assertEquals(SupplyOrderPreflight.Status.READY,
                new SupplyOrderPreflightService(dataSource).check(plotId, playerId).status());
        setup.delete(SupplySetupOwner.restaurant(plotId), SupplySetupPointType.DELIVERY_STOP);
        OperationKey operation = OperationKey.create();

        assertThrows(SupplyOrderSetupIncompleteException.class,
                () -> new SupplyOrderRepository(dataSource).captureAuthorized(
                        plotId, UUID.randomUUID(), order(UUID.randomUUID()), operation, catalog()));

        assertEquals(new CurrencyAmount(500), economy.balance(playerId));
        assertEquals(0, count("supply_orders"));
        assertEquals(0, countLedger(operation.value()));
    }

    @Test
    void rejectsDuplicateOperationWhenOrderLinesDifferDespiteSameTotal() throws SQLException {
        String plotId = "plot_1";
        new PlotAssignmentRepository(dataSource).assign(plotId, playerId, "paper-1");
        completeSetup(plotId);
        IngredientCatalog catalog = catalog();
        OperationKey operation = OperationKey.create();
        UUID orderId = UUID.randomUUID();
        SupplyOrderRepository repository = new SupplyOrderRepository(dataSource);
        SupplierOrder first = SupplierOrder.draft(playerId, playerId, catalog)
                .addLine("tomato", 2).submit(operation.value());
        SupplierOrder conflicting = SupplierOrder.draft(playerId, playerId, catalog)
                .addLine("rice", 50).submit(operation.value());

        repository.captureAuthorized(plotId, orderId, first, operation, catalog);

        assertThrows(SupplyOrderOperationConflictException.class,
                () -> repository.captureAuthorized(plotId, orderId, conflicting, operation, catalog));
        assertEquals(new CurrencyAmount(450), economy.balance(playerId));
        assertEquals(1, count("supply_orders"));
    }

    @Test
    void rejectsOrderBuiltFromNonAuthoritativeCatalog() throws SQLException {
        String plotId = "plot_1";
        new PlotAssignmentRepository(dataSource).assign(plotId, playerId, "paper-1");
        IngredientCatalog authoritative = catalog();
        IngredientCatalog forged = new IngredientCatalog(3,
                new IngredientDefinition("tomato", "Tomato", IngredientUnit.PIECE, 1, 100));
        SupplierOrder order = SupplierOrder.draft(playerId, playerId, forged)
                .addLine("tomato", 2).submit(UUID.randomUUID());

        assertThrows(SupplyOrderOperationConflictException.class,
                () -> new SupplyOrderRepository(dataSource).captureAuthorized(
                        plotId, UUID.randomUUID(), order, OperationKey.create(), authoritative));

        assertEquals(new CurrencyAmount(500), economy.balance(playerId));
        assertEquals(0, count("supply_orders"));
    }

    @Test
    void marketCaptureUsesDatabasePriceAndPersistsImmutableSnapshot() throws Exception {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        new vn.restauranttycoon.market.MarketRepository(dataSource).ensureOpenCycle(
                java.util.Map.of("tomato", 40L, "rice", 2L), now, Duration.ofMinutes(10));
        OperationKey capture = OperationKey.create();
        SupplierOrder order = order(UUID.randomUUID());
        SupplyOrderReceipt receipt = new SupplyOrderRepository(dataSource).captureMarket(
                UUID.randomUUID(), order, capture, catalog(), now.plusSeconds(1));

        assertFalse(receipt.duplicate());
        assertEquals(new CurrencyAmount(320), receipt.balance());
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT unit_price, market_cycle_id, price_snapshot FROM supply_order_lines WHERE sku = 'tomato'")) {
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(40L, result.getLong(1));
                assertEquals(40L, result.getLong(3));
                assertNotNull(result.getObject(2));
            }
        }
        assertEquals(2L, new vn.restauranttycoon.market.MarketRepository(dataSource)
                .findPrice("tomato", now.plusSeconds(1)).quantityDemanded());
    }

    @Test
    void marketRetryUsesImmutableSnapshotAfterCycleExpires() throws Exception {
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        vn.restauranttycoon.market.MarketRepository market = new vn.restauranttycoon.market.MarketRepository(dataSource);
        market.ensureOpenCycle(java.util.Map.of("tomato", 40L, "rice", 2L), now, Duration.ofMinutes(10));
        OperationKey capture = OperationKey.create();
        UUID orderId = UUID.randomUUID();
        SupplyOrderRepository repository = new SupplyOrderRepository(dataSource);
        repository.captureMarket(orderId, order(UUID.randomUUID()), capture, catalog(), now.plusSeconds(1));

        market.ensureOpenCycle(java.util.Map.of("tomato", 80L, "rice", 4L), now.plusSeconds(601), Duration.ofMinutes(10));
        SupplyOrderReceipt retry = repository.captureMarket(
                UUID.randomUUID(), order(UUID.randomUUID()), capture, catalog(), now.plusSeconds(602));

        assertTrue(retry.duplicate());
        assertEquals(new CurrencyAmount(320), retry.balance());
        assertEquals(80L, market.findPrice("tomato", now.plusSeconds(602)).unitPrice());
    }

    @Test
    void marketAuthorizedCaptureRequiresOwnedReadyPlot() throws Exception {
        String plotId = "plot_1";
        new PlotAssignmentRepository(dataSource).assign(plotId, playerId, "paper-1");
        completeSetup(plotId);
        Instant now = Instant.parse("2026-08-19T00:00:00Z");
        new vn.restauranttycoon.market.MarketRepository(dataSource).ensureOpenCycle(
                java.util.Map.of("tomato", 40L, "rice", 2L), now, Duration.ofMinutes(10));
        SupplierOrder submitted = order(UUID.randomUUID());

        SupplyOrderReceipt receipt = new SupplyOrderRepository(dataSource).captureMarketAuthorized(
                plotId, UUID.randomUUID(), submitted, OperationKey.create(), catalog(), now.plusSeconds(1));

        assertFalse(receipt.duplicate());
        assertEquals(new CurrencyAmount(320), receipt.balance());
        assertEquals(1, count("supply_orders"));
    }

    @Test
    void marketAuthorizedCaptureRejectsForeignPlotBeforePayment() throws Exception {
        String plotId = "plot_foreign";
        UUID foreignPlayer = UUID.randomUUID();
        economy.apply(foreignPlayer, OperationKey.create(), 500, "TEST_GRANT");
        new PlotAssignmentRepository(dataSource).assign(plotId, foreignPlayer, "paper-foreign");
        SupplierOrder submitted = order(UUID.randomUUID());
        OperationKey captureOperation = OperationKey.create();

        assertThrows(SupplyOrderAuthorizationException.class, () ->
                new SupplyOrderRepository(dataSource).captureMarketAuthorized(
                        plotId, UUID.randomUUID(), submitted, captureOperation, catalog(),
                        Instant.parse("2026-08-19T00:00:00Z")));

        assertEquals(0, count("supply_orders"));
        assertEquals(0, countLedger(captureOperation.value()));
    }

    private SupplierOrder order(UUID submitOperationId) {
        IngredientCatalog catalog = catalog();
        return SupplierOrder.draft(playerId, playerId, catalog)
                .addLine("tomato", 2)
                .addLine("rice", 50)
                .submit(submitOperationId);
    }

    private IngredientCatalog catalog() {
        return new IngredientCatalog(3,
                new IngredientDefinition("tomato", "Tomato", IngredientUnit.PIECE, new CurrencyAmount(25), 100),
                new IngredientDefinition("rice", "Rice", IngredientUnit.GRAM, new CurrencyAmount(1), 1_000));
    }

    private SupplySetupPointRepository completeSetup(String plotId) throws SQLException {
        SupplySetupPointRepository repository = new SupplySetupPointRepository(dataSource);
        for (SupplySetupPointType type : SupplySetupPointType.values()) {
            SupplySetupOwner owner = type.scope()
                    == vn.restauranttycoon.supplysetup.SupplySetupScope.CENTRAL_SUPPLIER
                    ? SupplySetupOwner.centralSupplier()
                    : SupplySetupOwner.restaurant(plotId);
            repository.upsert(new SupplySetupPoint(owner, type,
                    new SupplySetupPosition("world", type.ordinal(), 64, 0, 0, 0)));
        }
        return repository;
    }

    private int count(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }

    private int countLedger(UUID operationId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM economy_ledger WHERE operation_id = ?")) {
            statement.setObject(1, operationId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
