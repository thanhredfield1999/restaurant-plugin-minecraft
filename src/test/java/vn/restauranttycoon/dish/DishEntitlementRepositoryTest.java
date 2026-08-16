package vn.restauranttycoon.dish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.restauranttycoon.economy.OperationKey;

class DishEntitlementRepositoryTest {
    private DataSource dataSource;
    private DishEntitlementRepository entitlements;

    @BeforeEach
    void setUp() {
        JdbcDataSource database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource = database;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .load().migrate();
        entitlements = new DishEntitlementRepository(dataSource);
    }

    @Test
    void issuesOneDurableEntitlementPerOrder() throws Exception {
        UUID entitlementId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();

        DishEntitlement first = entitlements.issue(
                entitlementId, orderId, restaurantId, "grilled_cod", 3);
        DishEntitlement retry = entitlements.issue(
                entitlementId, orderId, restaurantId, "grilled_cod", 3);

        assertEquals(first, retry);
        assertEquals(DishEntitlementState.AVAILABLE, first.state());
        assertEquals(1, count("dish_entitlements"));
        assertThrows(DishEntitlementConflictException.class, () -> entitlements.issue(
                UUID.randomUUID(), orderId, restaurantId, "grilled_cod", 3));
        assertThrows(DishEntitlementConflictException.class, () -> entitlements.issue(
                entitlementId, UUID.randomUUID(), restaurantId, "grilled_cod", 3));
    }

    @Test
    void claimAndConsumeAreDurableAndIdempotent() throws Exception {
        DishEntitlement issued = issue();
        UUID playerId = UUID.randomUUID();
        OperationKey claimKey = OperationKey.create();
        OperationKey consumeKey = OperationKey.create();

        DishEntitlementResult claim = entitlements.claim(
                issued.entitlementId(), playerId, claimKey);
        DishEntitlementResult claimRetry = entitlements.claim(
                issued.entitlementId(), playerId, claimKey);
        DishEntitlementResult consumed = entitlements.consume(
                issued.entitlementId(), playerId, consumeKey);
        DishEntitlementResult consumeRetry = entitlements.consume(
                issued.entitlementId(), playerId, consumeKey);

        assertFalse(claim.duplicate());
        assertTrue(claimRetry.duplicate());
        assertEquals(DishEntitlementState.CLAIMED, claimRetry.entitlement().state());
        assertEquals(1, claimRetry.entitlement().stateRevision());
        assertFalse(consumed.duplicate());
        assertTrue(consumeRetry.duplicate());
        assertEquals(DishEntitlementState.CONSUMED, consumeRetry.entitlement().state());
        assertEquals(2, consumeRetry.entitlement().stateRevision());
        assertEquals(2, count("dish_entitlement_operations"));
    }

    @Test
    void competingPlayerCannotClaimOrConsumeTheDish() throws Exception {
        DishEntitlement issued = issue();
        UUID holder = UUID.randomUUID();
        entitlements.claim(issued.entitlementId(), holder, OperationKey.create());

        assertThrows(DishEntitlementConflictException.class, () -> entitlements.claim(
                issued.entitlementId(), UUID.randomUUID(), OperationKey.create()));
        assertThrows(DishEntitlementConflictException.class, () -> entitlements.consume(
                issued.entitlementId(), UUID.randomUUID(), OperationKey.create()));
        assertEquals(DishEntitlementState.CLAIMED,
                entitlements.find(issued.entitlementId()).orElseThrow().state());
        assertEquals(1, count("dish_entitlement_operations"));
    }

    @Test
    void operationKeyCannotBeReusedAcrossTransitionsOrPlayers() throws Exception {
        DishEntitlement first = issue();
        DishEntitlement second = entitlements.issue(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "bread", 1);
        UUID playerId = UUID.randomUUID();
        OperationKey key = OperationKey.create();
        entitlements.claim(first.entitlementId(), playerId, key);

        assertThrows(DishEntitlementConflictException.class, () -> entitlements.claim(
                second.entitlementId(), playerId, key));
        assertThrows(DishEntitlementConflictException.class, () -> entitlements.consume(
                first.entitlementId(), playerId, key));
    }

    @Test
    void concurrentIdenticalIssueCreatesOneEntitlement() throws Exception {
        UUID entitlementId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CompletableFuture<?>[] attempts = new CompletableFuture<?>[50];
            for (int index = 0; index < attempts.length; index++) {
                attempts[index] = CompletableFuture.supplyAsync(() -> {
                    try {
                        return entitlements.issue(
                                entitlementId, orderId, restaurantId, "grilled_cod", 1);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                }, executor);
            }
            CompletableFuture.allOf(attempts).get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, count("dish_entitlements"));
    }

    @Test
    void findsOnlyBoundedClaimedEntitlementsForHolder() throws Exception {
        UUID holder = UUID.randomUUID();
        DishEntitlement first = issue();
        DishEntitlement second = issue();
        DishEntitlement available = issue();
        DishEntitlement otherHolder = issue();
        entitlements.claim(first.entitlementId(), holder, OperationKey.create());
        entitlements.claim(second.entitlementId(), holder, OperationKey.create());
        entitlements.claim(otherHolder.entitlementId(), UUID.randomUUID(), OperationKey.create());

        List<DishEntitlement> found = entitlements.findClaimedByHolder(holder, 1);

        assertEquals(1, found.size());
        assertEquals(holder, found.get(0).holderId().orElseThrow());
        assertEquals(DishEntitlementState.CLAIMED, found.get(0).state());
        assertFalse(found.stream().anyMatch(entitlement ->
                entitlement.entitlementId().equals(available.entitlementId())));
        assertFalse(found.stream().anyMatch(entitlement ->
                entitlement.entitlementId().equals(otherHolder.entitlementId())));
    }

    @Test
    void rejectsUnboundedClaimedEntitlementQueryLimits() {
        UUID holder = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> entitlements.findClaimedByHolder(holder, 0));
        assertThrows(IllegalArgumentException.class,
                () -> entitlements.findClaimedByHolder(holder, 101));
    }

    @Test
    void findsBoundedEntitlementsByIdsWithoutHidingInvalidTokenStates() throws Exception {
        DishEntitlement claimed = issue();
        DishEntitlement consumed = issue();
        DishEntitlement omitted = issue();
        UUID holder = UUID.randomUUID();
        entitlements.claim(claimed.entitlementId(), holder, OperationKey.create());
        entitlements.claim(consumed.entitlementId(), holder, OperationKey.create());
        entitlements.consume(consumed.entitlementId(), holder, OperationKey.create());

        Map<UUID, DishEntitlement> found = entitlements.findByIds(Set.of(
                claimed.entitlementId(), consumed.entitlementId(), UUID.randomUUID()));

        assertEquals(Set.of(claimed.entitlementId(), consumed.entitlementId()), found.keySet());
        assertEquals(DishEntitlementState.CLAIMED, found.get(claimed.entitlementId()).state());
        assertEquals(DishEntitlementState.CONSUMED, found.get(consumed.entitlementId()).state());
        assertFalse(found.containsKey(omitted.entitlementId()));
        assertEquals(Map.of(), entitlements.findByIds(Set.of()));
    }

    @Test
    void rejectsUnboundedEntitlementIdLookup() {
        Set<UUID> ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(ignored -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toSet());

        assertThrows(IllegalArgumentException.class, () -> entitlements.findByIds(ids));
    }

    @Test
    void loadsProjectionAndObservedTokensAsOneReconciliationSnapshot() throws Exception {
        UUID holder = UUID.randomUUID();
        DishEntitlement projected = issue();
        DishEntitlement consumed = issue();
        DishEntitlement omitted = issue();
        entitlements.claim(projected.entitlementId(), holder, OperationKey.create());
        entitlements.claim(consumed.entitlementId(), holder, OperationKey.create());
        entitlements.consume(consumed.entitlementId(), holder, OperationKey.create());

        DishEntitlementReconciliationSnapshot snapshot = entitlements
                .loadReconciliationSnapshot(holder, 1, Set.of(
                        projected.entitlementId(), consumed.entitlementId()));

        assertEquals(Map.of(projected.entitlementId(), projected).keySet(),
                snapshot.projectionEntitlements().keySet());
        assertEquals(DishEntitlementState.CLAIMED,
                snapshot.projectionEntitlements().get(projected.entitlementId()).state());
        assertEquals(Set.of(projected.entitlementId(), consumed.entitlementId()),
                snapshot.tokenValidationEntitlements().keySet());
        assertEquals(DishEntitlementState.CONSUMED,
                snapshot.tokenValidationEntitlements().get(consumed.entitlementId()).state());
        assertFalse(snapshot.tokenValidationEntitlements().containsKey(omitted.entitlementId()));
    }

    @Test
    void loadsReconciliationSnapshotWithOneDatabaseStatement() throws Exception {
        UUID holder = UUID.randomUUID();
        DishEntitlement projected = issue();
        entitlements.claim(projected.entitlementId(), holder, OperationKey.create());
        AtomicInteger preparedStatements = new AtomicInteger();
        DishEntitlementRepository measured = new DishEntitlementRepository(
                countingDataSource(dataSource, preparedStatements));

        measured.loadReconciliationSnapshot(
                holder, 100, Set.of(projected.entitlementId()));

        assertEquals(1, preparedStatements.get());
    }

    @Test
    void rejectsUnboundedReconciliationSnapshotInputs() {
        UUID holder = UUID.randomUUID();
        Set<UUID> ids = java.util.stream.IntStream.range(0, 101)
                .mapToObj(ignored -> UUID.randomUUID())
                .collect(java.util.stream.Collectors.toSet());

        assertThrows(IllegalArgumentException.class,
                () -> entitlements.loadReconciliationSnapshot(holder, 0, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> entitlements.loadReconciliationSnapshot(holder, 1, ids));
    }

    private DishEntitlement issue() throws Exception {
        return entitlements.issue(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "grilled_cod", 1);
    }

    private DataSource countingDataSource(DataSource delegate, AtomicInteger preparedStatements) {
        return (DataSource) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, arguments) -> {
                    try {
                        Object result = method.invoke(delegate, arguments);
                        if (!method.getName().equals("getConnection")) {
                            return result;
                        }
                        Connection connection = (Connection) result;
                        return Proxy.newProxyInstance(
                                getClass().getClassLoader(), new Class<?>[]{Connection.class},
                                (connectionProxy, connectionMethod, connectionArguments) -> {
                                    if (connectionMethod.getName().equals("prepareStatement")) {
                                        preparedStatements.incrementAndGet();
                                    }
                                    try {
                                        return connectionMethod.invoke(connection, connectionArguments);
                                    } catch (InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                });
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                });
    }

    private int count(String table) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getInt(1);
        }
    }
}
