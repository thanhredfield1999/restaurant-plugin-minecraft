CREATE TABLE market_cycles (
    cycle_id UUID PRIMARY KEY,
    cycle_number BIGINT NOT NULL UNIQUE CHECK (cycle_number >= 0),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('OPEN', 'CLOSED')),
    CHECK (ends_at > starts_at)
);

CREATE TABLE market_prices (
    cycle_id UUID NOT NULL REFERENCES market_cycles(cycle_id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL,
    base_price BIGINT NOT NULL CHECK (base_price > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    demand_factor NUMERIC(8, 5) NOT NULL CHECK (demand_factor > 0),
    supply_factor NUMERIC(8, 5) NOT NULL CHECK (supply_factor > 0),
    quantity_demanded BIGINT NOT NULL DEFAULT 0 CHECK (quantity_demanded >= 0),
    quantity_supplied BIGINT NOT NULL DEFAULT 0 CHECK (quantity_supplied >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (cycle_id, sku)
);

CREATE TABLE market_demand_events (
    event_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE,
    cycle_id UUID NOT NULL REFERENCES market_cycles(cycle_id),
    sku VARCHAR(64) NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    event_type VARCHAR(16) NOT NULL CHECK (event_type IN ('PURCHASE', 'SUPPLY', 'ROLLBACK')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE supply_order_lines
    ADD COLUMN market_cycle_id UUID REFERENCES market_cycles(cycle_id);

ALTER TABLE supply_order_lines
    ADD COLUMN price_snapshot BIGINT CHECK (price_snapshot > 0);

ALTER TABLE supply_order_lines
    ADD CONSTRAINT supply_order_lines_market_snapshot_pair_ck
    CHECK ((market_cycle_id IS NULL AND price_snapshot IS NULL)
        OR (market_cycle_id IS NOT NULL AND price_snapshot IS NOT NULL));

CREATE INDEX market_prices_sku_idx ON market_prices (sku, cycle_id);
CREATE INDEX market_demand_events_cycle_sku_idx
    ON market_demand_events (cycle_id, sku, created_at);
CREATE INDEX supply_order_lines_market_cycle_idx
    ON supply_order_lines (market_cycle_id, sku);

COMMENT ON TABLE market_cycles IS 'Durable market pricing windows; PostgreSQL is source of truth.';
COMMENT ON TABLE market_prices IS 'Per-SKU price snapshot and aggregate supply-demand state per cycle.';
COMMENT ON TABLE market_demand_events IS 'Idempotent market demand/supply events keyed by operation_id.';
COMMENT ON COLUMN supply_order_lines.price_snapshot IS 'Price charged at order confirmation; later market changes do not affect order.';
