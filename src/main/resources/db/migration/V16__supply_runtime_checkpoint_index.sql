ALTER TABLE supply_shipment_runtime
    ADD COLUMN checkpoint_index INTEGER NOT NULL DEFAULT 0
        CHECK (checkpoint_index >= 0);

COMMENT ON COLUMN supply_shipment_runtime.checkpoint_index IS
    'Zero-based index within repeated ROUTE_WAYPOINT checkpoints; zero for non-waypoint stages';

CREATE INDEX supply_shipment_runtime_checkpoint_idx
    ON supply_shipment_runtime (checkpoint_stage, checkpoint_index, shipment_id);

UPDATE supply_shipment_runtime
SET checkpoint_index = 0
WHERE checkpoint_stage <> 'ROUTE_WAYPOINT';

ALTER TABLE supply_shipment_runtime
    ADD CONSTRAINT supply_runtime_checkpoint_index_shape CHECK (
        (checkpoint_stage = 'ROUTE_WAYPOINT' AND checkpoint_index >= 0)
        OR (checkpoint_stage <> 'ROUTE_WAYPOINT' AND checkpoint_index = 0)
    );
