ALTER TABLE supply_shipment_runtime
    ADD COLUMN entity_cleanup_state VARCHAR(16) NOT NULL DEFAULT 'NONE'
        CHECK (entity_cleanup_state IN ('NONE', 'REQUIRED', 'CONFIRMED'));

ALTER TABLE supply_shipment_runtime
    ADD COLUMN entity_cleanup_operation_id UUID;

CREATE UNIQUE INDEX supply_shipment_runtime_cleanup_operation_uidx
    ON supply_shipment_runtime (entity_cleanup_operation_id);

ALTER TABLE supply_shipment_runtime
    ADD CONSTRAINT supply_runtime_entity_cleanup_shape CHECK (
        (entity_cleanup_state = 'NONE' AND entity_cleanup_operation_id IS NULL)
        OR (entity_cleanup_state IN ('REQUIRED', 'CONFIRMED')
            AND entity_cleanup_operation_id IS NOT NULL)
    );

CREATE INDEX supply_shipment_runtime_cleanup_idx
    ON supply_shipment_runtime (entity_cleanup_state, shipment_id);
