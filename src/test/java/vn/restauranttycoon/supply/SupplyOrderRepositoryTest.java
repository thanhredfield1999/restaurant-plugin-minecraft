package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
