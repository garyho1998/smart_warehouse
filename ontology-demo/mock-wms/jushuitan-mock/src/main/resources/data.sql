INSERT INTO jst_warehouse (wms_co_id, name, modified_time) VALUES ('JST-WH-001','上海自营仓',CURRENT_TIMESTAMP);

INSERT INTO jst_location (slot_id, wms_co_id, area, floor, enabled, modified_time) VALUES ('SH_R1_S1','JST-WH-001','pick',1,true,CURRENT_TIMESTAMP);
INSERT INTO jst_location (slot_id, wms_co_id, area, floor, enabled, modified_time) VALUES ('SH_R1_S2','JST-WH-001','pick',1,true,CURRENT_TIMESTAMP);
INSERT INTO jst_location (slot_id, wms_co_id, area, floor, enabled, modified_time) VALUES ('SH_R2_S3','JST-WH-001','storage',2,true,CURRENT_TIMESTAMP);
INSERT INTO jst_location (slot_id, wms_co_id, area, floor, enabled, modified_time) VALUES ('SH_DK_01','JST-WH-001','dock',1,true,CURRENT_TIMESTAMP);

INSERT INTO jst_sku (sku_id, name, modified_time) VALUES ('JST-SKU-001','行動電源 10000mAh',CURRENT_TIMESTAMP);
INSERT INTO jst_sku (sku_id, name, modified_time) VALUES ('JST-SKU-002','Type-C 充電器',CURRENT_TIMESTAMP);

INSERT INTO jst_inventory (sku_id, slot_id, wms_co_id, qty, lock_qty, modified_time) VALUES ('JST-SKU-001','SH_R1_S1','JST-WH-001',120,5,CURRENT_TIMESTAMP);
INSERT INTO jst_inventory (sku_id, slot_id, wms_co_id, qty, lock_qty, modified_time) VALUES ('JST-SKU-002','SH_R1_S2','JST-WH-001',300,0,CURRENT_TIMESTAMP);
INSERT INTO jst_inventory (sku_id, slot_id, wms_co_id, qty, lock_qty, modified_time) VALUES ('JST-SKU-001','SH_R2_S3','JST-WH-001',50,0,CURRENT_TIMESTAMP);
