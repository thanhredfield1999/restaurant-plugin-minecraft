CREATE TABLE supply_route_waypoints (
    setup_scope VARCHAR(32) NOT NULL
        CHECK (setup_scope = 'RESTAURANT'),
    owner_id VARCHAR(64) NOT NULL,
    sequence_no INTEGER NOT NULL CHECK (sequence_no >= 1),
    waypoint_name VARCHAR(128) NOT NULL,
    world_name VARCHAR(255) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL CHECK (pitch >= -90 AND pitch <= 90),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (setup_scope, owner_id, sequence_no),
    CHECK (owner_id <> 'central_supplier')
);
