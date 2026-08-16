ALTER TABLE supply_shipments ADD COLUMN claimed_by_instance VARCHAR(64);
ALTER TABLE supply_shipments ADD COLUMN claim_token UUID;
ALTER TABLE supply_shipments ADD COLUMN claim_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE supply_shipments ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE supply_shipments ADD COLUMN last_error VARCHAR(1024);

ALTER TABLE supply_shipments
    ADD CONSTRAINT supply_shipments_claim_consistency CHECK (
        (claimed_by_instance IS NULL AND claim_token IS NULL AND claim_expires_at IS NULL)
        OR
        (claimed_by_instance IS NOT NULL AND claim_token IS NOT NULL AND claim_expires_at IS NOT NULL)
    );

CREATE INDEX supply_shipments_claimable_idx
    ON supply_shipments (state, claim_expires_at, shipment_id);

ALTER TABLE supply_packages ADD COLUMN received_by UUID;
ALTER TABLE supply_packages ADD COLUMN received_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE supply_packages
    ADD CONSTRAINT supply_packages_received_consistency CHECK (
        (state = 'IN_TRANSIT' AND received_by IS NULL AND received_at IS NULL)
        OR
        (state IN ('HANDED_OFF', 'STOCKED') AND received_by IS NOT NULL AND received_at IS NOT NULL)
    );
