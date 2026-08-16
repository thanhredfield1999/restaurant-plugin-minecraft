CREATE TABLE supply_setup_points (
    setup_scope VARCHAR(32) NOT NULL
        CHECK (setup_scope IN ('CENTRAL_SUPPLIER', 'RESTAURANT')),
    owner_id VARCHAR(64) NOT NULL,
    point_type VARCHAR(32) NOT NULL
        CHECK (point_type IN (
            'ORDER_DESK', 'SUPPLIER_SPAWN', 'DELIVERY_ENTRY', 'DELIVERY_STOP',
            'UNLOAD_POINT', 'WAREHOUSE_ENTRY', 'DELIVERY_EXIT', 'DELIVERY_DESPAWN'
        )),
    world_name VARCHAR(255) NOT NULL,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    z DOUBLE PRECISION NOT NULL,
    yaw REAL NOT NULL,
    pitch REAL NOT NULL CHECK (pitch >= -90 AND pitch <= 90),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (setup_scope, owner_id, point_type),
    CHECK (
        (setup_scope = 'CENTRAL_SUPPLIER' AND owner_id = 'central_supplier'
            AND point_type IN ('ORDER_DESK', 'SUPPLIER_SPAWN'))
        OR
        (setup_scope = 'RESTAURANT' AND owner_id <> 'central_supplier'
            AND point_type IN (
                'DELIVERY_ENTRY', 'DELIVERY_STOP', 'UNLOAD_POINT',
                'WAREHOUSE_ENTRY', 'DELIVERY_EXIT', 'DELIVERY_DESPAWN'
            ))
    )
);
