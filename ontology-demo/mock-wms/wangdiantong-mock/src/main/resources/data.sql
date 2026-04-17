INSERT INTO wdt_warehouse (warehouse_no, warehouse_name, is_main, modified_at) VALUES ('WDT-SZ-001','深圳自营仓',true,CURRENT_TIMESTAMP);

INSERT INTO wdt_location (bin_code, warehouse_no, zone_name, status, modified_at) VALUES ('A-01-01','WDT-SZ-001','揀貨區','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO wdt_location (bin_code, warehouse_no, zone_name, status, modified_at) VALUES ('A-01-02','WDT-SZ-001','揀貨區','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO wdt_location (bin_code, warehouse_no, zone_name, status, modified_at) VALUES ('B-02-01','WDT-SZ-001','儲存區','ACTIVE',CURRENT_TIMESTAMP);
INSERT INTO wdt_location (bin_code, warehouse_no, zone_name, status, modified_at) VALUES ('C-01-01','WDT-SZ-001','收貨區','ACTIVE',CURRENT_TIMESTAMP);

INSERT INTO wdt_sku (spec_no, goods_no, goods_name, unit, modified_at) VALUES ('WDT-SPEC-001','WDT-G-001','USB-C線 1m','件',CURRENT_TIMESTAMP);
INSERT INTO wdt_sku (spec_no, goods_no, goods_name, unit, modified_at) VALUES ('WDT-SPEC-002','WDT-G-002','無線滑鼠','件',CURRENT_TIMESTAMP);
INSERT INTO wdt_sku (spec_no, goods_no, goods_name, unit, modified_at) VALUES ('WDT-SPEC-003','WDT-G-003','機械鍵盤','件',CURRENT_TIMESTAMP);

INSERT INTO wdt_stock (spec_no, bin_code, stock_num, lock_num, modified_at) VALUES ('WDT-SPEC-001','A-01-01',200,10,CURRENT_TIMESTAMP);
INSERT INTO wdt_stock (spec_no, bin_code, stock_num, lock_num, modified_at) VALUES ('WDT-SPEC-002','A-01-02',150,5,CURRENT_TIMESTAMP);
INSERT INTO wdt_stock (spec_no, bin_code, stock_num, lock_num, modified_at) VALUES ('WDT-SPEC-003','B-02-01',80,0,CURRENT_TIMESTAMP);
