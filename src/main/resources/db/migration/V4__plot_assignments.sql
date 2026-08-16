CREATE TABLE plot_assignments (
    plot_id VARCHAR(64) PRIMARY KEY,
    account_id UUID UNIQUE REFERENCES economy_accounts(account_id),
    server_id VARCHAR(64) NOT NULL,
    fence_token BIGINT NOT NULL CHECK (fence_token > 0),
    assignment_revision BIGINT NOT NULL CHECK (assignment_revision > 0),
    assigned_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (account_id IS NULL AND assigned_at IS NULL)
        OR
        (account_id IS NOT NULL AND assigned_at IS NOT NULL)
    )
);
