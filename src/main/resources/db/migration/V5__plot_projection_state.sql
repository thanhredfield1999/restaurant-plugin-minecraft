CREATE TABLE plot_projection_state (
    plot_id VARCHAR(64) PRIMARY KEY REFERENCES plot_assignments(plot_id),
    fence_token BIGINT NOT NULL CHECK (fence_token > 0),
    stage_revision BIGINT NOT NULL CHECK (stage_revision >= 0),
    last_world_operation_id UUID REFERENCES world_operations(world_operation_id),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
