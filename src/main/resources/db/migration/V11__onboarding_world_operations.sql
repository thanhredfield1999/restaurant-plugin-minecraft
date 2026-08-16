ALTER TABLE world_operations
    ALTER COLUMN purchase_id DROP NOT NULL;

ALTER TABLE world_operations
    ADD COLUMN source_account_id UUID REFERENCES economy_accounts(account_id);

ALTER TABLE world_operations
    ADD COLUMN operation_source VARCHAR(32) NOT NULL DEFAULT 'PURCHASE';

ALTER TABLE world_operations
    ADD CONSTRAINT world_operations_source_check CHECK (
        (operation_source = 'PURCHASE' AND purchase_id IS NOT NULL AND source_account_id IS NULL)
        OR
        (operation_source = 'ONBOARDING' AND purchase_id IS NULL AND source_account_id IS NOT NULL)
    );

-- Onboarding idempotency is serialized by the plot-assignment row lock in requestInitialStage.
-- No cross-source unique index: paid purchases may legitimately share plot/fence/stage.
