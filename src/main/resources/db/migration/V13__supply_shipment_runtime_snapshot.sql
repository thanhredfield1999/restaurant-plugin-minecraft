CREATE TABLE supply_shipment_runtime (
    shipment_id UUID PRIMARY KEY REFERENCES supply_shipments(shipment_id) ON DELETE CASCADE,
    revision BIGINT NOT NULL CHECK (revision >= 1),
    checkpoint_stage VARCHAR(32) NOT NULL
        CHECK (checkpoint_stage IN (
            'DELIVERY_ENTRY', 'ROUTE_WAYPOINT', 'DELIVERY_STOP', 'UNLOAD_POINT',
            'DELIVERY_EXIT', 'DELIVERY_DESPAWN', 'PENDING_MANUAL'
        )),
    journey_snapshot_version INTEGER NOT NULL CHECK (journey_snapshot_version >= 1),
    journey_snapshot VARCHAR(16384) NOT NULL CHECK (LENGTH(journey_snapshot) > 0),
    unload_deadline_at TIMESTAMP WITH TIME ZONE,
    recovery_outcome VARCHAR(32) NOT NULL DEFAULT 'NONE'
        CHECK (recovery_outcome IN ('NONE', 'PENDING_MANUAL')),
    last_transition_operation_id UUID UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (
        (checkpoint_stage = 'UNLOAD_POINT' AND unload_deadline_at IS NOT NULL AND recovery_outcome = 'NONE')
        OR
        (checkpoint_stage = 'PENDING_MANUAL' AND unload_deadline_at IS NULL AND recovery_outcome = 'PENDING_MANUAL')
        OR
        (checkpoint_stage NOT IN ('UNLOAD_POINT', 'PENDING_MANUAL')
            AND unload_deadline_at IS NULL AND recovery_outcome = 'NONE')
    )
);

CREATE INDEX supply_shipment_runtime_recovery_idx
    ON supply_shipment_runtime (checkpoint_stage, unload_deadline_at, shipment_id);

INSERT INTO supply_shipment_runtime (
    shipment_id, revision, checkpoint_stage, journey_snapshot_version, journey_snapshot,
    recovery_outcome
)
SELECT shipment_id, 1, 'PENDING_MANUAL', 1, '{"schema":"legacy-v12-no-runtime-snapshot"}',
       'PENDING_MANUAL'
FROM supply_shipments;
