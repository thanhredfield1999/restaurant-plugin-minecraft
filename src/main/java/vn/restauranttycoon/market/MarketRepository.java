package vn.restauranttycoon.market;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import vn.restauranttycoon.economy.OperationKey;

public final class MarketRepository {
    private final DataSource dataSource;

    public MarketRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public MarketCycle ensureOpenCycle(Map<String, Long> basePrices, Instant now, Duration duration)
            throws SQLException {
        Objects.requireNonNull(basePrices, "basePrices");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(duration, "duration");
        if (basePrices.isEmpty() || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("Market cycle requires prices and positive duration");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MarketCycle current = findOpen(connection, now);
                if (current != null) {
                    connection.commit();
                    return current;
                }
                closeExpiredCycles(connection, now);
                long nextNumber = nextCycleNumber(connection);
                UUID cycleId = UUID.randomUUID();
                Instant ends = now.plus(duration);
                insertCycle(connection, cycleId, nextNumber, now, ends);
                for (Map.Entry<String, Long> entry : basePrices.entrySet()) {
                    if (entry.getValue() == null || entry.getValue() <= 0) {
                        throw new IllegalArgumentException("Base price must be positive: " + entry.getKey());
                    }
                    insertPrice(connection, cycleId, entry.getKey(), entry.getValue());
                }
                connection.commit();
                return new MarketCycle(cycleId, nextNumber, now, ends, "OPEN");
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    MarketCycle winner = findOpen(connection, now);
                    if (winner != null) {
                        connection.commit();
                        return winner;
                    }
                }
                throw exception;
            } catch (RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public java.util.Map<String, MarketPrice> lockPrices(
            Connection connection, java.util.List<String> skus, Instant now
    ) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(skus, "skus");
        Objects.requireNonNull(now, "now");
        if (skus.isEmpty()) throw new IllegalArgumentException("Market order requires at least one SKU");
        java.util.Map<String, MarketPrice> prices = new java.util.LinkedHashMap<>();
        for (String sku : skus) {
            MarketPrice price = lockCurrentPrice(connection, sku, now);
            if (prices.put(sku, price) != null) {
                throw new IllegalArgumentException("Duplicate market SKU: " + sku);
            }
            if (price == null) throw new IllegalStateException("No open market price for SKU: " + sku);
        }
        return java.util.Map.copyOf(prices);
    }

    public MarketEventResult recordPurchase(
            Connection connection, OperationKey operation, String sku, long quantity, Instant now
    ) throws SQLException {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(now, "now");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        MarketPrice price = lockCurrentPrice(connection, sku, now);
        if (price == null) throw new IllegalStateException("No open market price for SKU: " + sku);
        ExistingEvent existing = findEvent(connection, operation.value());
        if (existing != null) {
            if (!existing.matches(price.cycleId(), sku, quantity, "PURCHASE")) {
                throw new MarketOperationConflictException("Market operation payload conflict: " + operation.value());
            }
            return new MarketEventResult(true, existing.eventId(), price);
        }
        UUID eventId = UUID.randomUUID();
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO market_demand_events (event_id, operation_id, cycle_id, sku, quantity, event_type) "
                        + "VALUES (?, ?, ?, ?, ?, 'PURCHASE')")) {
            insert.setObject(1, eventId); insert.setObject(2, operation.value());
            insert.setObject(3, price.cycleId()); insert.setString(4, sku); insert.setLong(5, quantity);
            insert.executeUpdate();
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE market_prices SET quantity_demanded = quantity_demanded + ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE cycle_id = ? AND sku = ?")) {
            update.setLong(1, quantity); update.setObject(2, price.cycleId()); update.setString(3, sku);
            update.executeUpdate();
        }
        return new MarketEventResult(false, eventId, readUpdatedPrice(connection, price.cycleId(), sku));
    }

    public MarketPrice findPrice(String sku, Instant now) throws SQLException {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(now, "now");
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT c.cycle_id, c.cycle_number, p.sku, p.base_price, p.unit_price, "
                             + "p.demand_factor, p.supply_factor, p.quantity_demanded, p.quantity_supplied "
                             + "FROM market_cycles c JOIN market_prices p ON p.cycle_id = c.cycle_id "
                             + "WHERE c.state = 'OPEN' AND c.starts_at <= ? AND c.ends_at > ? AND p.sku = ?")) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, sku);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? readPrice(result) : null;
            }
        }
    }

    public MarketEventResult recordPurchase(OperationKey operation, String sku, long quantity, Instant now)
            throws SQLException {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(now, "now");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MarketPrice price = lockCurrentPrice(connection, sku, now);
                if (price == null) throw new IllegalStateException("No open market price for SKU: " + sku);
                ExistingEvent existing = findEvent(connection, operation.value());
                if (existing != null) {
                    if (!existing.matches(price.cycleId(), sku, quantity, "PURCHASE")) {
                        throw new MarketOperationConflictException("Market operation payload conflict: " + operation.value());
                    }
                    connection.commit();
                    return new MarketEventResult(true, existing.eventId(), price);
                }
                UUID eventId = UUID.randomUUID();
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO market_demand_events "
                                + "(event_id, operation_id, cycle_id, sku, quantity, event_type) "
                                + "VALUES (?, ?, ?, ?, ?, 'PURCHASE')")) {
                    insert.setObject(1, eventId);
                    insert.setObject(2, operation.value());
                    insert.setObject(3, price.cycleId());
                    insert.setString(4, sku);
                    insert.setLong(5, quantity);
                    insert.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE market_prices SET quantity_demanded = quantity_demanded + ?, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE cycle_id = ? AND sku = ?")) {
                    update.setLong(1, quantity);
                    update.setObject(2, price.cycleId());
                    update.setString(3, sku);
                    update.executeUpdate();
                }
                connection.commit();
                return new MarketEventResult(false, eventId, readUpdatedPrice(connection, price.cycleId(), sku));
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private MarketPrice lockCurrentPrice(Connection connection, String sku, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT c.cycle_id, c.cycle_number, p.sku, p.base_price, p.unit_price, "
                        + "p.demand_factor, p.supply_factor, p.quantity_demanded, p.quantity_supplied "
                        + "FROM market_cycles c JOIN market_prices p ON p.cycle_id = c.cycle_id "
                        + "WHERE c.state = 'OPEN' AND c.starts_at <= ? AND c.ends_at > ? AND p.sku = ? FOR UPDATE")) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, sku);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? readPrice(result) : null; }
        }
    }

    private MarketPrice readUpdatedPrice(Connection connection, UUID cycleId, String sku) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT c.cycle_id, c.cycle_number, p.sku, p.base_price, p.unit_price, "
                        + "p.demand_factor, p.supply_factor, p.quantity_demanded, p.quantity_supplied "
                        + "FROM market_cycles c JOIN market_prices p ON p.cycle_id = c.cycle_id "
                        + "WHERE c.cycle_id = ? AND p.sku = ?")) {
            statement.setObject(1, cycleId); statement.setString(2, sku);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Market price row disappeared");
                return readPrice(result);
            }
        }
    }

    private static MarketPrice readPrice(ResultSet result) throws SQLException {
        return new MarketPrice(result.getObject(1, UUID.class), result.getLong(2), result.getString(3),
                result.getLong(4), result.getLong(5), result.getDouble(6), result.getDouble(7),
                result.getLong(8), result.getLong(9));
    }

    private MarketCycle findOpen(Connection c, Instant now) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT cycle_id, cycle_number, starts_at, ends_at, state FROM market_cycles "
                        + "WHERE state = 'OPEN' AND starts_at <= ? AND ends_at > ? FOR UPDATE")) {
            s.setTimestamp(1, Timestamp.from(now)); s.setTimestamp(2, Timestamp.from(now));
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                return new MarketCycle(r.getObject(1, UUID.class), r.getLong(2),
                        r.getTimestamp(3).toInstant(), r.getTimestamp(4).toInstant(), r.getString(5));
            }
        }
    }

    private void closeExpiredCycles(Connection c, Instant now) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE market_cycles SET state = 'CLOSED', open_lock_key = NULL "
                        + "WHERE state = 'OPEN' AND ends_at <= ?")) {
            s.setTimestamp(1, Timestamp.from(now));
            s.executeUpdate();
        }
    }

    private long nextCycleNumber(Connection c) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT COALESCE(MAX(cycle_number), -1) + 1 FROM market_cycles")) {
            try (ResultSet r = s.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }

    private void insertCycle(Connection c, UUID id, long number, Instant start, Instant end) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO market_cycles (cycle_id, cycle_number, starts_at, ends_at, state, open_lock_key) "
                        + "VALUES (?, ?, ?, ?, 'OPEN', 'OPEN')")) {
            s.setObject(1, id); s.setLong(2, number); s.setTimestamp(3, Timestamp.from(start)); s.setTimestamp(4, Timestamp.from(end)); s.executeUpdate();
        }
    }

    private void insertPrice(Connection c, UUID cycleId, String sku, long base) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO market_prices (cycle_id, sku, base_price, unit_price, demand_factor, supply_factor) "
                        + "VALUES (?, ?, ?, ?, 1.0, 1.0)")) {
            s.setObject(1, cycleId); s.setString(2, sku); s.setLong(3, base); s.setLong(4, base); s.executeUpdate();
        }
    }

    private ExistingEvent findEvent(Connection c, UUID operationId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT event_id, cycle_id, sku, quantity, event_type FROM market_demand_events WHERE operation_id = ? FOR UPDATE")) {
            s.setObject(1, operationId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? new ExistingEvent(r.getObject(1, UUID.class), r.getObject(2, UUID.class), r.getString(3), r.getLong(4), r.getString(5)) : null;
            }
        }
    }

    private record ExistingEvent(UUID eventId, UUID cycleId, String sku, long quantity, String type) {
        boolean matches(UUID cycle, String expectedSku, long expectedQuantity, String expectedType) {
            return cycle.equals(cycleId) && expectedSku.equals(sku) && expectedQuantity == quantity && expectedType.equals(type);
        }
    }
}
