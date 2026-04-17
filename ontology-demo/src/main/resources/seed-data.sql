INSERT INTO warehouse (id, code, name, address, wms_type) VALUES
    ('WH-SZ-001', 'SZ-WH-001', 'Shenzhen Factory Warehouse', 'Shenzhen Baoan District', 'FLUX');

INSERT INTO zone (id, warehouse_id, code, name, type, capacity) VALUES
    ('ZONE-RECEIVE', 'WH-SZ-001', 'RECV', 'Receive Zone', 'RECEIVE', 120),
    ('ZONE-STORAGE', 'WH-SZ-001', 'STOR', 'Storage Zone', 'STORAGE', 400),
    ('ZONE-PICK', 'WH-SZ-001', 'PICK', 'Pick Zone', 'PICK', 180),
    ('ZONE-DOCK', 'WH-SZ-001', 'DOCK', 'Dock Zone', 'DOCK', 60);

INSERT INTO location (id, zone_id, code, floor, type, max_weight_kg, occupied) VALUES
    ('LOC-RECEIVE-01', 'ZONE-RECEIVE', 'RCV-01', 1, 'FLOOR', 500.0000, TRUE),
    ('LOC-RECEIVE-02', 'ZONE-RECEIVE', 'RCV-02', 1, 'FLOOR', 500.0000, FALSE),
    ('LOC-RECEIVE-03', 'ZONE-RECEIVE', 'RCV-03', 1, 'FLOOR', 500.0000, FALSE),
    ('LOC-RECEIVE-04', 'ZONE-RECEIVE', 'RCV-04', 1, 'FLOOR', 500.0000, FALSE),
    ('LOC-STORAGE-01', 'ZONE-STORAGE', 'STR-01', 1, 'SHELF', 300.0000, TRUE),
    ('LOC-STORAGE-02', 'ZONE-STORAGE', 'STR-02', 1, 'SHELF', 300.0000, TRUE),
    ('LOC-STORAGE-03', 'ZONE-STORAGE', 'STR-03', 1, 'SHELF', 300.0000, TRUE),
    ('LOC-STORAGE-04', 'ZONE-STORAGE', 'STR-04', 1, 'SHELF', 300.0000, TRUE),
    ('LOC-PICK-01', 'ZONE-PICK', 'PCK-01', 1, 'BIN', 80.0000, TRUE),
    ('LOC-PICK-02', 'ZONE-PICK', 'PCK-02', 1, 'BIN', 80.0000, TRUE),
    ('LOC-PICK-03', 'ZONE-PICK', 'PCK-03', 1, 'BIN', 80.0000, TRUE),
    ('LOC-PICK-04', 'ZONE-PICK', 'PCK-04', 1, 'BIN', 80.0000, FALSE),
    ('LOC-DOCK-01', 'ZONE-DOCK', 'DCK-01', 1, 'DOCK_DOOR', 1000.0000, TRUE),
    ('LOC-DOCK-02', 'ZONE-DOCK', 'DCK-02', 1, 'DOCK_DOOR', 1000.0000, TRUE),
    ('LOC-DOCK-03', 'ZONE-DOCK', 'DCK-03', 1, 'DOCK_DOOR', 1000.0000, FALSE),
    ('LOC-DOCK-04', 'ZONE-DOCK', 'DCK-04', 1, 'DOCK_DOOR', 1000.0000, FALSE);

INSERT INTO sku (id, code, name, barcode, weight_kg, category) VALUES
    ('SKU-001', 'SKU-CABLE', 'USB-C Cable', '690000000001', 0.0200, 'Accessories'),
    ('SKU-002', 'SKU-MOUSE', 'Wireless Mouse', '690000000002', 0.0900, 'Accessories'),
    ('SKU-003', 'SKU-KEYBOARD', 'Mechanical Keyboard', '690000000003', 0.8500, 'Peripherals'),
    ('SKU-004', 'SKU-MONITOR', '27 Inch Monitor', '690000000004', 4.5000, 'Displays'),
    ('SKU-005', 'SKU-BATTERY', 'Robot Battery Pack', '690000000005', 12.0000, 'Maintenance'),
    ('SKU-006', 'SKU-MOTOR', 'Drive Motor', '690000000006', 45.0000, 'Spare Parts');

INSERT INTO inventory (id, location_id, sku_id, quantity, lot_number) VALUES
    ('INV-001', 'LOC-STORAGE-01', 'SKU-001', 500, 'LOT-A1'),
    ('INV-002', 'LOC-STORAGE-01', 'SKU-002', 200, 'LOT-A2'),
    ('INV-003', 'LOC-STORAGE-02', 'SKU-003', 120, 'LOT-B1'),
    ('INV-004', 'LOC-STORAGE-02', 'SKU-004', 40, 'LOT-B2'),
    ('INV-005', 'LOC-STORAGE-03', 'SKU-005', 18, 'LOT-C1'),
    ('INV-006', 'LOC-STORAGE-03', 'SKU-006', 8, 'LOT-C2'),
    ('INV-007', 'LOC-PICK-01', 'SKU-001', 80, 'LOT-P1'),
    ('INV-008', 'LOC-PICK-02', 'SKU-002', 60, 'LOT-P2'),
    ('INV-009', 'LOC-PICK-03', 'SKU-003', 25, 'LOT-P3'),
    ('INV-010', 'LOC-RECEIVE-01', 'SKU-005', 4, 'LOT-R1');

INSERT INTO warehouse_order (id, warehouse_id, external_id, type, status, priority) VALUES
    ('ORD-IN-001', 'WH-SZ-001', 'INB-20260414-001', 'INBOUND', 'IN_PROGRESS', 50),
    ('ORD-OUT-001', 'WH-SZ-001', 'OUT-20260414-001', 'OUTBOUND', 'IN_PROGRESS', 90),
    ('ORD-OUT-002', 'WH-SZ-001', 'OUT-20260414-002', 'OUTBOUND', 'PENDING', 70);

INSERT INTO order_line (id, order_id, sku_id, quantity, picked_quantity) VALUES
    ('LINE-001', 'ORD-IN-001', 'SKU-005', 4, 0),
    ('LINE-002', 'ORD-OUT-001', 'SKU-001', 30, 10),
    ('LINE-003', 'ORD-OUT-001', 'SKU-002', 20, 0),
    ('LINE-004', 'ORD-OUT-002', 'SKU-003', 15, 0);

INSERT INTO robot (id, code, brand, model, status, battery_pct, current_location_id, assigned_zone_id) VALUES
    ('R-HIK-001', 'R-HIK-001', 'HIKROBOT', 'LatentMover-A', 'BUSY', 76, 'LOC-PICK-01', 'ZONE-PICK'),
    ('R-GEK-001', 'R-GEK-001', 'GEEKPLUS', 'P800', 'IDLE', 15, 'LOC-PICK-02', 'ZONE-PICK');

INSERT INTO task (id, order_line_id, type, status, priority, robot_id, from_location_id, to_location_id, assigned_at, completed_at) VALUES
    ('TSK-001', 'LINE-002', 'PICK', 'ASSIGNED', 90, 'R-HIK-001', 'LOC-STORAGE-01', 'LOC-DOCK-01', TIMESTAMP '2026-04-14 08:30:00', NULL),
    ('TSK-002', 'LINE-003', 'PICK', 'IN_PROGRESS', 85, 'R-HIK-001', 'LOC-STORAGE-02', 'LOC-DOCK-01', TIMESTAMP '2026-04-14 08:45:00', NULL),
    ('TSK-003', 'LINE-001', 'PUTAWAY', 'PENDING', 50, NULL, 'LOC-RECEIVE-01', 'LOC-STORAGE-03', NULL, NULL),
    ('TSK-004', 'LINE-004', 'MOVE', 'COMPLETED', 60, 'R-HIK-001', 'LOC-STORAGE-04', 'LOC-PICK-03', TIMESTAMP '2026-04-14 07:50:00', TIMESTAMP '2026-04-14 08:20:00'),
    ('TSK-005', 'LINE-002', 'PICK', 'FAILED', 100, 'R-GEK-001', 'LOC-STORAGE-03', 'LOC-DOCK-02', TIMESTAMP '2026-04-14 09:00:00', NULL);
