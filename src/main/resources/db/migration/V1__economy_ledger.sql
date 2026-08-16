CREATE TABLE economy_accounts (
    account_id UUID PRIMARY KEY,
    balance BIGINT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE economy_ledger (
    ledger_id BIGSERIAL PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE,
    account_id UUID NOT NULL REFERENCES economy_accounts(account_id),
    delta BIGINT NOT NULL CHECK (delta <> 0),
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    account_revision BIGINT NOT NULL CHECK (account_revision > 0),
    reason VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX economy_ledger_account_created_idx
    ON economy_ledger (account_id, created_at DESC);
