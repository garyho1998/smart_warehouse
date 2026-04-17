package com.warehouse.ontology.adapter.wangdiantong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.warehouse.ontology.adapter.OntologyRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WangdiantongAdapterTest {

    private final WangdiantongClient client = mock(WangdiantongClient.class);
    private final WangdiantongAdapter adapter = new WangdiantongAdapter(client);

    @Test
    void name_is_wangdiantong() {
        assertThat(adapter.name()).isEqualTo("wangdiantong");
    }

    @Test
    void maps_wdt_warehouse_to_ontology() {
        when(client.queryWarehouses(any())).thenReturn(List.of(
                new WdtWarehouseDto("WDT-SZ-001", "深圳自营仓", true, Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullWarehouses(Instant.EPOCH);

        assertThat(records).hasSize(1);
        OntologyRecord wh = records.get(0);
        assertThat(wh.type()).isEqualTo("Warehouse");
        Map<String, Object> p = wh.properties();
        assertThat(p.get("id")).isEqualTo("WDT-SZ-001");
        assertThat(p.get("code")).isEqualTo("WDT-SZ-001");
        assertThat(p.get("name")).isEqualTo("深圳自营仓");
        assertThat(p.get("wmsType")).isEqualTo("WDT");
    }

    @Test
    void maps_wdt_location_emits_zone_and_location_with_chinese_normalized() {
        when(client.queryLocations(any())).thenReturn(List.of(
                new WdtLocationDto("A-01-01", "WDT-SZ-001", "揀貨區", "ACTIVE", Instant.now()),
                new WdtLocationDto("A-01-02", "WDT-SZ-001", "揀貨區", "ACTIVE", Instant.now()),
                new WdtLocationDto("B-02-01", "WDT-SZ-001", "儲存區", "ACTIVE", Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullLocations(Instant.EPOCH);

        List<OntologyRecord> zones = records.stream().filter(r -> r.type().equals("Zone")).toList();
        List<OntologyRecord> locs = records.stream().filter(r -> r.type().equals("Location")).toList();

        assertThat(zones).hasSize(2);
        assertThat(zones).extracting(r -> r.properties().get("type"))
                .containsExactlyInAnyOrder("PICK", "STORAGE");
        assertThat(zones).allSatisfy(z ->
                assertThat(z.properties().get("warehouseId")).isEqualTo("WDT-SZ-001"));

        assertThat(locs).hasSize(3);
        OntologyRecord firstLoc = locs.get(0);
        assertThat(firstLoc.properties().get("code")).isEqualTo("A-01-01");
        assertThat(firstLoc.properties().get("zoneId")).isEqualTo("WDT-SZ-001-PICK");
        assertThat(firstLoc.properties().get("floor")).isEqualTo(1);
        assertThat(firstLoc.properties().get("type")).isEqualTo("BIN");
    }

    @Test
    void maps_wdt_sku_to_ontology() {
        when(client.querySkus(any())).thenReturn(List.of(
                new WdtSkuDto("WDT-SPEC-001", "WDT-G-001", "USB-C線 1m", "件", Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullSkus(Instant.EPOCH);

        assertThat(records).hasSize(1);
        Map<String, Object> p = records.get(0).properties();
        assertThat(records.get(0).type()).isEqualTo("Sku");
        assertThat(p.get("id")).isEqualTo("WDT-SPEC-001");
        assertThat(p.get("code")).isEqualTo("WDT-SPEC-001");
        assertThat(p.get("name")).isEqualTo("USB-C線 1m");
    }

    @Test
    void maps_wdt_stock_to_inventory() {
        when(client.queryStock(any())).thenReturn(List.of(
                new WdtStockDto("WDT-SPEC-001", "A-01-01", 200, 10, Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullInventory(Instant.EPOCH);

        assertThat(records).hasSize(1);
        Map<String, Object> p = records.get(0).properties();
        assertThat(records.get(0).type()).isEqualTo("Inventory");
        assertThat(p.get("skuId")).isEqualTo("WDT-SPEC-001");
        assertThat(p.get("locationId")).isEqualTo("A-01-01");
        assertThat(p.get("quantity")).isEqualTo(200);
    }
}
