package com.warehouse.ontology.adapter.wangdiantong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.warehouse.ontology.OntologyDemoApplication;
import com.warehouse.ontology.adapter.sync.WmsSyncScheduler;
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
class WangdiantongIntegrationTest {

    @Autowired
    WmsSyncScheduler scheduler;

    @Autowired
    GenericRepository repo;

    @MockBean
    WangdiantongClient client;

    @Test
    void sync_pulls_wdt_into_ontology() {
        Instant now = Instant.now();
        when(client.queryWarehouses(any())).thenReturn(List.of(
                new WdtWarehouseDto("WDT-SZ-001", "深圳自营仓", true, now)
        ));
        when(client.queryLocations(any())).thenReturn(List.of(
                new WdtLocationDto("A-01-01", "WDT-SZ-001", "揀貨區", "ACTIVE", now),
                new WdtLocationDto("B-02-01", "WDT-SZ-001", "儲存區", "ACTIVE", now)
        ));
        when(client.querySkus(any())).thenReturn(List.of(
                new WdtSkuDto("WDT-SPEC-001", "WDT-G-001", "USB-C線 1m", "件", now)
        ));
        when(client.queryStock(any())).thenReturn(List.of(
                new WdtStockDto("WDT-SPEC-001", "A-01-01", 200, 10, now)
        ));

        scheduler.runOnce();

        List<Map<String, Object>> warehouses = repo.findAll("Warehouse");
        assertThat(warehouses).extracting(m -> m.get("id")).contains("WDT-SZ-001");

        List<Map<String, Object>> locations = repo.findAll("Location");
        assertThat(locations).extracting(m -> m.get("code"))
                .contains("A-01-01", "B-02-01");

        List<Map<String, Object>> skus = repo.findAll("Sku");
        assertThat(skus).extracting(m -> m.get("id")).contains("WDT-SPEC-001");

        List<Map<String, Object>> inv = repo.findAll("Inventory");
        assertThat(inv).extracting(m -> m.get("skuId")).contains("WDT-SPEC-001");
    }
}
