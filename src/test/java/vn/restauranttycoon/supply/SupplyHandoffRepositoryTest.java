package vn.restauranttycoon.supply;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupplyHandoffRepositoryTest {
    private DataSource dataSource;
    private SupplyFulfillmentRepository repository;
    private UUID order = UUID.randomUUID();
    private UUID restaurant = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        Flyway.configure().dataSource(db).locations("classpath:db/migration").load().migrate();
        dataSource = db;
        repository = new SupplyFulfillmentRepository(db);
        try (Connection c = db.getConnection()) {
            insert(c, "INSERT INTO supply_orders (order_id, operation_id, restaurant_id, player_id, catalog_version, total, state) VALUES (?, ?, ?, ?, 1, 100, 'SUBMITTED')", order, UUID.randomUUID(), restaurant, UUID.randomUUID());
            insert(c, "INSERT INTO supply_order_lines (order_id, sku, display_name, unit, quantity, unit_price) VALUES (?, 'tomato', 'Tomato', 'PIECE', 4, 25)", order);
            insert(c, "INSERT INTO supply_payments (order_id, amount, state, capture_operation_id) VALUES (?, 100, 'CAPTURED', ?)", order, UUID.randomUUID());
        }
    }

    @Test
    void handoffAndStockAreExactlyOnce() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        UUID shipment = fulfillment.shipmentId();
        UUID pack = fulfillment.packageId();
        repository.dispatch(shipment);
        repository.arrive(shipment);
        UUID operation = UUID.randomUUID();
        repository.handoff(shipment, operation);
        repository.handoff(shipment, operation);
        UUID stockOperation = UUID.randomUUID();
        repository.stock(pack, restaurant, stockOperation);
        repository.stock(pack, restaurant, stockOperation);
        assertEquals(4, repository.quantity(restaurant, "tomato"));
    }

    @Test
    void stockRejectsPackageFromAnotherRestaurant() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        repository.dispatch(fulfillment.shipmentId());
        repository.arrive(fulfillment.shipmentId());
        repository.handoff(fulfillment.shipmentId(), UUID.randomUUID());
        UUID otherRestaurant = UUID.randomUUID();

        assertThrows(IllegalStateException.class,
                () -> repository.stock(fulfillment.packageId(), otherRestaurant, UUID.randomUUID()));
        assertEquals(0, repository.quantity(otherRestaurant, "tomato"));
        assertEquals(0, repository.quantity(restaurant, "tomato"));
    }

    @Test
    void authorizedHandoffRejectsForeignRestaurantBeforeStateMutation() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        repository.dispatch(fulfillment.shipmentId());
        repository.arrive(fulfillment.shipmentId());
        UUID foreign = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () ->
                repository.handoffAuthorized(
                        fulfillment.shipmentId(), foreign, foreign, UUID.randomUUID()));

        assertEquals(0, repository.quantity(restaurant, "tomato"));
        repository.handoff(fulfillment.shipmentId(), UUID.randomUUID());
    }

    @Test
    void packageAuthorizedHandoffResolvesShipmentAndIsIdempotent() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        repository.dispatch(fulfillment.shipmentId());
        repository.arrive(fulfillment.shipmentId());
        UUID operation = UUID.randomUUID();

        repository.handoffPackageAuthorized(fulfillment.packageId(), restaurant, restaurant, operation);
        repository.handoffPackageAuthorized(fulfillment.packageId(), restaurant, restaurant, operation);
        repository.stock(fulfillment.packageId(), restaurant, UUID.randomUUID());

        assertEquals(4, repository.quantity(restaurant, "tomato"));
    }

    @Test
    void authorizedHandoffSeparatesRestaurantScopeFromReceivingActor() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        repository.dispatch(fulfillment.shipmentId());
        repository.arrive(fulfillment.shipmentId());
        UUID receivingWorker = UUID.randomUUID();

        repository.handoffPackageAuthorized(
                fulfillment.packageId(), restaurant, receivingWorker, UUID.randomUUID());
        repository.stock(fulfillment.packageId(), restaurant, UUID.randomUUID());

        assertEquals(4, repository.quantity(restaurant, "tomato"));
    }

    @Test
    void sameOperationIdWithDifferentReceivingActorIsRejected() throws Exception {
        SupplyFulfillmentRecord fulfillment = repository.createForPaidOrder(order, restaurant);
        repository.dispatch(fulfillment.shipmentId());
        repository.arrive(fulfillment.shipmentId());
        UUID operation = UUID.randomUUID();
        UUID firstActor = UUID.randomUUID();
        UUID secondActor = UUID.randomUUID();

        repository.handoffPackageAuthorized(fulfillment.packageId(), restaurant, firstActor, operation);

        assertThrows(IllegalStateException.class, () ->
                repository.handoffPackageAuthorized(fulfillment.packageId(), restaurant, secondActor, operation));
    }

    private static void insert(Connection c, String sql, Object... values) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) s.setObject(i + 1, values[i]);
            s.executeUpdate();
        }
    }
}
