CREATE TABLE supply_package_lines (
    package_id UUID NOT NULL REFERENCES supply_packages(package_id) ON DELETE CASCADE,
    sku VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (package_id, sku)
);

INSERT INTO supply_package_lines (package_id, sku, quantity)
SELECT p.package_id, l.sku, l.quantity
FROM supply_packages p
JOIN supply_shipments s ON s.shipment_id = p.shipment_id
JOIN supply_order_lines l ON l.order_id = s.order_id;
