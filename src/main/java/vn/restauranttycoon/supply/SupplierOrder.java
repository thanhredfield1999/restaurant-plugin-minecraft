package vn.restauranttycoon.supply;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import vn.restauranttycoon.economy.CurrencyAmount;

public final class SupplierOrder {
    private final UUID restaurantId;
    private final UUID playerId;
    private final IngredientCatalog catalog;
    private final int catalogVersion;
    private final Map<String, SupplierOrderLine> lines;
    private final SupplierOrderState state;
    private final UUID transitionOperation;

    private SupplierOrder(
            UUID restaurantId,
            UUID playerId,
            IngredientCatalog catalog,
            int catalogVersion,
            Map<String, SupplierOrderLine> lines,
            SupplierOrderState state,
            UUID transitionOperation
    ) {
        this.restaurantId = restaurantId;
        this.playerId = playerId;
        this.catalog = catalog;
        this.catalogVersion = catalogVersion;
        this.lines = Map.copyOf(lines);
        this.state = state;
        this.transitionOperation = transitionOperation;
    }

    public static SupplierOrder draft(UUID restaurantId, UUID playerId, IngredientCatalog catalog) {
        return new SupplierOrder(
                Objects.requireNonNull(restaurantId, "restaurantId"),
                Objects.requireNonNull(playerId, "playerId"),
                catalog,
                catalog.version(),
                new LinkedHashMap<>(),
                SupplierOrderState.DRAFT,
                null);
    }

    public SupplierOrder addLine(String sku, int quantity) {
        requireState(SupplierOrderState.DRAFT);
        if (lines.containsKey(sku)) {
            throw new IllegalArgumentException("Duplicate ingredient SKU: " + sku);
        }
        IngredientCatalog catalog = this.catalog;
        if (lines.containsKey(sku)) {
            throw new IllegalArgumentException("Duplicate ingredient SKU: " + sku);
        }
        IngredientDefinition definition = catalog.require(sku);
        if (quantity > definition.maxQuantity()) {
            throw new IllegalArgumentException("Quantity exceeds ingredient limit");
        }
        Map<String, SupplierOrderLine> next = new LinkedHashMap<>(lines);
        next.put(sku, new SupplierOrderLine(
                definition.sku(), definition.displayName(), definition.unit(), quantity, definition.unitPrice()));
        return copy(next, state, transitionOperation);
    }

    public SupplierOrder withMarketPrices(java.util.Map<String, CurrencyAmount> prices) {
        requireState(SupplierOrderState.DRAFT);
        Objects.requireNonNull(prices, "prices");
        Map<String, SupplierOrderLine> next = new LinkedHashMap<>();
        for (SupplierOrderLine line : lines.values()) {
            CurrencyAmount price = prices.get(line.sku());
            if (price == null) throw new IllegalArgumentException("Missing market price: " + line.sku());
            next.put(line.sku(), new SupplierOrderLine(
                    line.sku(), line.displayName(), line.unit(), line.quantity(), price));
        }
        return copy(next, state, transitionOperation);
    }

    public SupplierOrder repriceMarket(java.util.Map<String, CurrencyAmount> prices) {
        if (state != SupplierOrderState.SUBMITTED) {
            throw new IllegalStateException("Only submitted orders can be repriced");
        }
        Objects.requireNonNull(prices, "prices");
        Map<String, SupplierOrderLine> next = new LinkedHashMap<>();
        for (SupplierOrderLine line : lines.values()) {
            CurrencyAmount price = prices.get(line.sku());
            if (price == null) throw new IllegalArgumentException("Missing market price: " + line.sku());
            next.put(line.sku(), new SupplierOrderLine(line.sku(), line.displayName(), line.unit(), line.quantity(), price));
        }
        return copy(next, state, transitionOperation);
    }

    public SupplierOrder submit(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        if (state == SupplierOrderState.SUBMITTED && operationId.equals(transitionOperation)) {
            return this;
        }
        if (state == SupplierOrderState.SUBMITTED) {
            throw new OperationConflictException(operationId);
        }
        requireState(SupplierOrderState.DRAFT);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot submit an empty order");
        }
        return copy(lines, SupplierOrderState.SUBMITTED, operationId);
    }

    public SupplierOrder cancel(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        if (state == SupplierOrderState.CANCELLED && operationId.equals(transitionOperation)) {
            return this;
        }
        if (state != SupplierOrderState.SUBMITTED) {
            throw new IllegalStateException("Only submitted orders can be cancelled");
        }
        if (operationId.equals(transitionOperation)) {
            throw new OperationConflictException(operationId);
        }
        return copy(lines, SupplierOrderState.CANCELLED, operationId);
    }

    public UUID restaurantId() { return restaurantId; }
    public UUID playerId() { return playerId; }
    public int catalogVersion() { return catalogVersion; }
    public List<SupplierOrderLine> lines() { return List.copyOf(lines.values()); }
    public SupplierOrderState state() { return state; }
    public CurrencyAmount total() {
        long total = 0;
        for (SupplierOrderLine line : lines.values()) {
            total = Math.addExact(total, line.subtotal().units());
        }
        return new CurrencyAmount(total);
    }

    private SupplierOrder copy(
            Map<String, SupplierOrderLine> nextLines,
            SupplierOrderState nextState,
            UUID nextOperation
    ) {
        return new SupplierOrder(restaurantId, playerId, catalog, catalogVersion, nextLines, nextState, nextOperation);
    }

    private void requireState(SupplierOrderState expected) {
        if (state != expected) {
            throw new IllegalStateException("Order is not in state " + expected);
        }
    }
}
