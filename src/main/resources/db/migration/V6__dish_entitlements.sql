CREATE TABLE dish_entitlements (
    entitlement_id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    restaurant_id UUID NOT NULL,
    recipe_id VARCHAR(64) NOT NULL,
    recipe_version BIGINT NOT NULL CHECK (recipe_version > 0),
    state VARCHAR(32) NOT NULL CHECK (state IN ('AVAILABLE', 'CLAIMED', 'CONSUMED')),
    holder_id UUID,
    state_revision BIGINT NOT NULL DEFAULT 0 CHECK (state_revision >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((state = 'AVAILABLE' AND holder_id IS NULL)
        OR (state IN ('CLAIMED', 'CONSUMED') AND holder_id IS NOT NULL))
);

CREATE TABLE dish_entitlement_operations (
    operation_id UUID PRIMARY KEY,
    entitlement_id UUID NOT NULL REFERENCES dish_entitlements(entitlement_id),
    operation_type VARCHAR(16) NOT NULL CHECK (operation_type IN ('CLAIM', 'CONSUME')),
    player_id UUID NOT NULL,
    resulting_state VARCHAR(32) NOT NULL CHECK (resulting_state IN ('CLAIMED', 'CONSUMED')),
    resulting_revision BIGINT NOT NULL CHECK (resulting_revision > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX dish_entitlements_holder_state_idx
    ON dish_entitlements (holder_id, state);
