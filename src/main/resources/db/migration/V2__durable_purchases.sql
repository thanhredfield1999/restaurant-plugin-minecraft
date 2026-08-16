CREATE TABLE unlocks (
    account_id UUID NOT NULL REFERENCES economy_accounts(account_id),
    unlock_id VARCHAR(64) NOT NULL,
    purchased_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    price_paid BIGINT NOT NULL CHECK (price_paid > 0),
    definition_version BIGINT NOT NULL CHECK (definition_version > 0),
    PRIMARY KEY (account_id, unlock_id)
);

CREATE TABLE purchases (
    purchase_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES economy_accounts(account_id),
    unlock_id VARCHAR(64) NOT NULL,
    definition_version BIGINT NOT NULL CHECK (definition_version > 0),
    price BIGINT NOT NULL CHECK (price > 0),
    state VARCHAR(32) NOT NULL CHECK (state = 'COMMITTED'),
    plot_id VARCHAR(64) NOT NULL,
    plot_fence_token BIGINT NOT NULL CHECK (plot_fence_token >= 0),
    target_stage_revision BIGINT NOT NULL CHECK (target_stage_revision > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (account_id, unlock_id)
);

CREATE TABLE world_operations (
    world_operation_id UUID PRIMARY KEY,
    purchase_id UUID NOT NULL UNIQUE REFERENCES purchases(purchase_id),
    operation_type VARCHAR(32) NOT NULL CHECK (operation_type = 'APPLY_STAGE'),
    plot_id VARCHAR(64) NOT NULL,
    required_fence_token BIGINT NOT NULL CHECK (required_fence_token >= 0),
    target_stage_revision BIGINT NOT NULL CHECK (target_stage_revision > 0),
    state VARCHAR(32) NOT NULL CHECK (state IN ('REQUESTED', 'APPLYING', 'APPLIED', 'REPAIR_REQUIRED', 'CANCELLED_BY_ADMIN')),
    phase VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    claimed_by_instance VARCHAR(64),
    claim_expires_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX world_operations_pending_idx
    ON world_operations (state, created_at);
