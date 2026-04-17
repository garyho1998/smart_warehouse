package com.warehouse.ontology.adapter.shipbob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.warehouse.ontology.adapter.OntologyRecord;
import com.warehouse.ontology.adapter.shipbob.SbLocationDto.SbRegionDto;
import com.warehouse.ontology.adapter.shipbob.SbProductDto.SbInventoryRef;
import com.warehouse.ontology.adapter.shipbob.SbProductDto.SbVariantDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShipBobAdapterTest {

    private final ShipBobClient client = mock(ShipBobClient.class);
    private final ShipBobAdapter adapter = new ShipBobAdapter(client);

    @Test
    void name_is_shipbob() {
        assertThat(adapter.name()).isEqualTo("shipbob");
    }

    @Test
    void maps_active_location_to_warehouse() {
        when(client.queryLocations()).thenReturn(List.of(
                new SbLocationDto(19, "Twin Lakes (WI)", "MKE-1", true, true,
                        new SbRegionDto(3, "Midwest")),
                new SbLocationDto(99, "Inactive FC", "XX-1", false, false, null)
        ));

        List<OntologyRecord> records = adapter.pullWarehouses(Instant.EPOCH);

        assertThat(records).hasSize(1);
        Map<String, Object> p = records.get(0).properties();
        assertThat(records.get(0).type()).isEqualTo("Warehouse");
        assertThat(p.get("id")).isEqualTo("SB-19");
        assertThat(p.get("code")).isEqualTo("MKE-1");
        assertThat(p.get("name")).isEqualTo("Twin Lakes (WI)");
        assertThat(p.get("wmsType")).isEqualTo("SHIPBOB");
    }

    @Test
    void maps_location_to_zone() {
        when(client.queryLocations()).thenReturn(List.of(
                new SbLocationDto(22, "Phoenix (AZ)", "PHX-1", true, true,
                        new SbRegionDto(1, "West Coast"))
        ));

        List<OntologyRecord> zones = adapter.pullLocations(Instant.EPOCH);

        assertThat(zones).hasSize(1);
        Map<String, Object> p = zones.get(0).properties();
        assertThat(zones.get(0).type()).isEqualTo("Zone");
        assertThat(p.get("warehouseId")).isEqualTo("SB-22");
        assertThat(p.get("type")).isEqualTo("STORAGE");
    }

    @Test
    void maps_active_variant_to_sku() {
        when(client.queryProducts(Instant.EPOCH)).thenReturn(List.of(
                new SbProductDto(1001, "Demo Product 1", List.of(
                        new SbVariantDto(17423372, "DEMO-SKU-001", "Demo SKU-001", "Active",
                                null, new SbInventoryRef(10311184, 50))
                ))
        ));

        List<OntologyRecord> skus = adapter.pullSkus(Instant.EPOCH);

        assertThat(skus).hasSize(1);
        Map<String, Object> p = skus.get(0).properties();
        assertThat(skus.get(0).type()).isEqualTo("Sku");
        assertThat(p.get("id")).isEqualTo("SB-17423372");
        assertThat(p.get("code")).isEqualTo("DEMO-SKU-001");
        assertThat(p.get("name")).isEqualTo("Demo SKU-001");
    }

    @Test
    void maps_variant_inventory_to_ontology() {
        when(client.queryProducts(Instant.EPOCH)).thenReturn(List.of(
                new SbProductDto(1001, "Demo Product 1", List.of(
                        new SbVariantDto(17423372, "DEMO-SKU-001", "Demo SKU-001", "Active",
                                null, new SbInventoryRef(10311184, 75))
                ))
        ));

        List<OntologyRecord> inventory = adapter.pullInventory(Instant.EPOCH);

        assertThat(inventory).hasSize(1);
        Map<String, Object> p = inventory.get(0).properties();
        assertThat(inventory.get(0).type()).isEqualTo("Inventory");
        assertThat(p.get("skuId")).isEqualTo("SB-17423372");
        assertThat(p.get("quantity")).isEqualTo(75);
    }
}
