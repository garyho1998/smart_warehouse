package com.warehouse.ontology.adapter.shipbob;

import com.warehouse.ontology.adapter.OntologyRecord;
import com.warehouse.ontology.adapter.WmsAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ShipBobAdapter implements WmsAdapter {

    private final ShipBobClient client;

    public ShipBobAdapter(ShipBobClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "shipbob";
    }

    @Override
    public List<OntologyRecord> pullWarehouses(Instant since) {
        return client.queryLocations().stream()
                .filter(SbLocationDto::isActive)
                .map(loc -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", "SB-" + loc.id());
                    m.put("code", loc.abbreviation());
                    m.put("name", loc.name());
                    m.put("wmsType", "SHIPBOB");
                    return new OntologyRecord("Warehouse", m);
                })
                .toList();
    }

    @Override
    public List<OntologyRecord> pullLocations(Instant since) {
        // ShipBob locations are fulfilment centres — model each as a Zone
        List<OntologyRecord> out = new ArrayList<>();
        for (SbLocationDto loc : client.queryLocations()) {
            if (!loc.isActive()) continue;
            String warehouseId = "SB-" + loc.id();
            String region = loc.region() != null ? loc.region().name() : "Unknown";
            Map<String, Object> zp = new HashMap<>();
            zp.put("id", warehouseId + "-STORAGE");
            zp.put("warehouseId", warehouseId);
            zp.put("code", "STORAGE");
            zp.put("name", region);
            zp.put("type", "STORAGE");
            out.add(new OntologyRecord("Zone", zp));
        }
        return out;
    }

    @Override
    public List<OntologyRecord> pullSkus(Instant since) {
        return client.queryProducts(since).stream()
                .flatMap(p -> p.variants().stream())
                .filter(v -> "Active".equals(v.status()))
                .map(v -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", "SB-" + v.id());
                    m.put("code", v.sku());
                    m.put("name", v.name());
                    return new OntologyRecord("Sku", m);
                })
                .toList();
    }

    @Override
    public List<OntologyRecord> pullInventory(Instant since) {
        return client.queryProducts(since).stream()
                .flatMap(p -> p.variants().stream())
                .filter(v -> v.inventory() != null)
                .map(v -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", "SB-INV-" + v.inventory().inventoryId());
                    m.put("skuId", "SB-" + v.id());
                    m.put("quantity", v.inventory().onHandQty());
                    return new OntologyRecord("Inventory", m);
                })
                .toList();
    }
}
