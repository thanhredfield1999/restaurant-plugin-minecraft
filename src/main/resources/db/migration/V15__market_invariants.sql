ALTER TABLE market_cycles
    ADD COLUMN open_lock_key VARCHAR(4);

UPDATE market_cycles SET open_lock_key = 'OPEN' WHERE state = 'OPEN';

ALTER TABLE market_cycles
    ADD CONSTRAINT market_cycles_open_lock_ck
    CHECK ((state = 'OPEN' AND open_lock_key = 'OPEN')
        OR (state <> 'OPEN' AND open_lock_key IS NULL));

CREATE UNIQUE INDEX market_cycles_one_open_idx
    ON market_cycles (open_lock_key);

ALTER TABLE market_demand_events
    ADD COLUMN reversal_of_event_id UUID REFERENCES market_demand_events(event_id);

CREATE UNIQUE INDEX market_demand_events_one_reversal_idx
    ON market_demand_events (reversal_of_event_id);

ALTER TABLE market_prices
    ADD CONSTRAINT market_prices_cycle_sku_key UNIQUE (cycle_id, sku);

ALTER TABLE supply_order_lines
    ADD CONSTRAINT supply_order_lines_market_price_fk
    FOREIGN KEY (market_cycle_id, sku)
    REFERENCES market_prices (cycle_id, sku);

COMMENT ON COLUMN market_demand_events.reversal_of_event_id IS
    'Original event reversed by this ROLLBACK event; one reversal per original event.';
