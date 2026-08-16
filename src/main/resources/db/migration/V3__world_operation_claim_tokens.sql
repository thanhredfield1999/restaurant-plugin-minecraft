ALTER TABLE world_operations
    ADD COLUMN claim_token UUID;

ALTER TABLE world_operations
    ADD CONSTRAINT world_operations_claim_consistency CHECK (
        (claimed_by_instance IS NULL AND claim_token IS NULL AND claim_expires_at IS NULL)
        OR
        (claimed_by_instance IS NOT NULL AND claim_token IS NOT NULL AND claim_expires_at IS NOT NULL)
    );

CREATE INDEX world_operations_claimable_idx
    ON world_operations (state, claim_expires_at, created_at);
