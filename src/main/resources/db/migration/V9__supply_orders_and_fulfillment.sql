CREATE TABLE supply_orders (
    order_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE,
    restaurant_id UUID NOT NULL,
    player_id UUID NOT NULL,
    catalog_version INTEGER NOT NULL CHECK (catalog_version > 0),
    total BIGINT NOT NULL CHECK (total > 0),
    state VARCHAR(32) NOT NULL CHECK (state IN ('SUBMITTED', 'CANCELLED', 'FULFILLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE supply_order_lines (
    order_id UUID NOT NULL REFERENCES supply_orders(order_id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price BIGINT NOT NULL CHECK (unit_price > 0),
    PRIMARY KEY (order_id, sku)
);

CREATE TABLE supply_payments (
    order_id UUID PRIMARY KEY REFERENCES supply_orders(order_id),
    amount BIGINT NOT NULL CHECK (amount > 0),
    state VARCHAR(32) NOT NULL CHECK (state IN ('CAPTURED', 'REFUNDED')),
    capture_operation_id UUID NOT NULL UNIQUE,
    refund_operation_id UUID UNIQUE
);

CREATE TABLE supply_shipments (
    shipment_id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES supply_orders(order_id),
    restaurant_id UUID NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN ('CREATED', 'IN_TRANSIT', 'ARRIVED', 'HANDED_OFF', 'CANCELLED')),
    handoff_operation_id UUID UNIQUE
);

CREATE TABLE supply_packages (
    package_id UUID PRIMARY KEY,
    shipment_id UUID NOT NULL UNIQUE REFERENCES supply_shipments(shipment_id),
    state VARCHAR(32) NOT NULL CHECK (state IN ('IN_TRANSIT', 'HANDED_OFF', 'STOCKED')),
    stocked_operation_id UUID UNIQUE
);

CREATE TABLE warehouse_stock (
    restaurant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    PRIMARY KEY (restaurant_id, sku)
);

CREATE TABLE warehouse_stock_operations (
    operation_id UUID PRIMARY KEY,
    package_id UUID NOT NULL UNIQUE REFERENCES supply_packages(package_id),
    restaurant_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX supply_shipments_pending_idx ON supply_shipments(state);
CREATE INDEX supply_packages_pending_idx ON supply_packages(state);
