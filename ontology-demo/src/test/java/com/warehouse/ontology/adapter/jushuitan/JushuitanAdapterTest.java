package com.warehouse.ontology.adapter.jushuitan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.warehouse.ontology.adapter.OntologyRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JushuitanAdapterTest {

    private final JushuitanClient client = mock(JushuitanClient.class);
    private final JushuitanAdapter adapter = new JushuitanAdapter(client);

    @Test
    void name_is_jushuitan() {
        assertThat(adapter.name()).isEqualTo("jushuitan");
    }

    @Test
    void maps_partner_to_warehouse() {
        when(client.queryPartners(any())).thenReturn(List.of(
                new JstWarehouseDto("JST-WH-001", "上海自营仓", Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullWarehouses(Instant.EPOCH);

        assertThat(records).hasSize(1);
        Map<String, Object> p = records.get(0).properties();
        assertThat(records.get(0).type()).isEqualTo("Warehouse");
        assertThat(p.get("id")).isEqualTo("JST-WH-001");
        assertThat(p.get("name")).isEqualTo("上海自营仓");
        assertThat(p.get("wmsType")).isEqualTo("JST");
    }

    @Test
    void maps_slot_to_zone_and_location_with_english_enum_normalized() {
        when(client.querySlots(any())).thenReturn(List.of(
                new JstLocationDto("SH_R1_S1", "JST-WH-001", "pick", 1, true, Instant.now()),
                new JstLocationDto("SH_R2_S3", "JST-WH-001", "storage", 2, true, Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullLocations(Instant.EPOCH);

        List<OntologyRecord> zones = records.stream().filter(r -> r.type().equals("Zone")).toList();
        List<OntologyRecord> locs = records.stream().filter(r -> r.type().equals("Location")).toList();

        assertThat(zones).extracting(r -> r.properties().get("type"))
                .containsExactlyInAnyOrder("PICK", "STORAGE");
        assertThat(locs).hasSize(2);

        OntologyRecord firstLoc = locs.get(0);
        assertThat(firstLoc.properties().get("code")).isEqualTo("SH_R1_S1");
        assertThat(firstLoc.properties().get("zoneId")).isEqualTo("JST-WH-001-PICK");
        assertThat(firstLoc.properties().get("floor")).isEqualTo(1);
    }

    @Test
    void maps_sku_to_ontology() {
        when(client.querySkus(any())).thenReturn(List.of(
                new JstSkuDto("JST-SKU-001", "行動電源 10000mAh", Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullSkus(Instant.EPOCH);

        assertThat(records).hasSize(1);
        Map<String, Object> p = records.get(0).properties();
        assertThat(records.get(0).type()).isEqualTo("Sku");
        assertThat(p.get("id")).isEqualTo("JST-SKU-001");
        assertThat(p.get("name")).isEqualTo("行動電源 10000mAh");
    }

    @Test
    void maps_inventory_to_ontology() {
        when(client.queryInventory(any())).thenReturn(List.of(
                new JstInventoryDto(1L, "JST-SKU-001", "SH_R1_S1", "JST-WH-001", 120, 5, Instant.now())
        ));

        List<OntologyRecord> records = adapter.pullInventory(Instant.EPOCH);

        assertThat(records).hasSize(1);
        Map<String, Object> p = records.get(0).properties();
        assertThat(records.get(0).type()).isEqualTo("Inventory");
        assertThat(p.get("skuId")).isEqualTo("JST-SKU-001");
        assertThat(p.get("locationId")).isEqualTo("SH_R1_S1");
        assertThat(p.get("quantity")).isEqualTo(120);
    }
}
