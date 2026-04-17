package com.warehouse.ontology.adapter.jushuitan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.warehouse.ontology.OntologyDemoApplication;
import com.warehouse.ontology.adapter.sync.WmsSyncScheduler;
import com.warehouse.ontology.adapter.wangdiantong.WangdiantongClient;
import com.warehouse.ontology.adapter.wangdiantong.WdtLocationDto;
import com.warehouse.ontology.adapter.wangdiantong.WdtSkuDto;
import com.warehouse.ontology.adapter.wangdiantong.WdtStockDto;
import com.warehouse.ontology.adapter.wangdiantong.WdtWarehouseDto;
import com.warehouse.ontology.engine.GenericRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = OntologyDemoApplication.class, properties = {
        "wms.sync.interval-ms=3600000"
})
@Transactional
class JushuitanIntegrationTest {

    @Autowired
    WmsSyncScheduler scheduler;

    @Autowired
    GenericRepository repo;

    @MockBean
    WangdiantongClient wdtClient;

    @MockBean
    JushuitanClient jstClient;

    @Test
    void sync_pulls_from_both_wms_into_single_ontology() {
        Instant now = Instant.now();

        when(wdtClient.queryWarehouses(any())).thenReturn(List.of(
                new WdtWarehouseDto("WDT-SZ-001", "深圳自营仓", true, now)
        ));
        when(wdtClient.queryLocations(any())).thenReturn(List.of(
                new WdtLocationDto("A-01-01", "WDT-SZ-001", "揀貨區", "ACTIVE", now)
        ));
        when(wdtClient.querySkus(any())).thenReturn(List.of(
                new WdtSkuDto("WDT-SPEC-001", "WDT-G-001", "USB-C線", "件", now)
        ));
        when(wdtClient.queryStock(any())).thenReturn(List.of(
                new WdtStockDto("WDT-SPEC-001", "A-01-01", 200, 10, now)
        ));

        when(jstClient.queryPartners(any())).thenReturn(List.of(
                new JstWarehouseDto("JST-WH-001", "上海自营仓", "生效")
        ));
        when(jstClient.querySlots(any())).thenReturn(List.of(
                new JstLocationDto("SH_R2_S3", "JST-WH-001", "storage", 2, true, now)
        ));
        when(jstClient.querySkus(any())).thenReturn(List.of(
                new JstSkuDto("JST-SKU-001", "行動電源", now)
        ));
        when(jstClient.queryInventory(any())).thenReturn(List.of(
                new JstInventoryDto("INV-001", "JST-SKU-001", "行動電源", 120, null)
        ));

        scheduler.runOnce();

        List<Map<String, Object>> warehouses = repo.findAll("Warehouse");
        assertThat(warehouses).extracting(m -> m.get("id"))
                .contains("WDT-SZ-001", "JST-WH-001");

        List<Map<String, Object>> locations = repo.findAll("Location");
        assertThat(locations).extracting(m -> m.get("code"))
                .contains("A-01-01", "SH_R2_S3");

        List<Map<String, Object>> inventory = repo.findAll("Inventory");
        assertThat(inventory).extracting(m -> m.get("skuId"))
                .contains("WDT-SPEC-001", "JST-SKU-001");
    }
}
