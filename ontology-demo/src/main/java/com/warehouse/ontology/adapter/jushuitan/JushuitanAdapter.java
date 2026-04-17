package com.warehouse.ontology.adapter.jushuitan;

import com.warehouse.ontology.adapter.OntologyRecord;
import com.warehouse.ontology.adapter.WmsAdapter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JushuitanAdapter implements WmsAdapter {

    private static final Map<String, String> ZONE_MAP = Map.of(
            "storage", "STORAGE",
            "pick", "PICK",
            "dock", "DOCK",
            "receive", "RECEIVE",
            "staging", "STAGING",
            "qc", "QC"
    );

    private final JushuitanClient client;

    public JushuitanAdapter(JushuitanClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "jushuitan";
    }

    @Override
    public List<OntologyRecord> pullWarehouses(Instant since) {
        return client.queryPartners(since).stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.wmsCoId());
                    m.put("code", p.wmsCoId());
                    m.put("name", p.name());
                    m.put("wmsType", "JST");
                    return new OntologyRecord("Warehouse", m);
                })
                .toList();
    }

    @Override
    public List<OntologyRecord> pullLocations(Instant since) {
        List<OntologyRecord> out = new ArrayList<>();
        Set<String> emittedZoneIds = new HashSet<>();
        for (JstLocationDto loc : client.querySlots(since)) {
            String zoneType = ZONE_MAP.getOrDefault(loc.area(), "STORAGE");
            String zoneId = loc.wmsCoId() + "-" + zoneType;
            if (emittedZoneIds.add(zoneId)) {
                Map<String, Object> zp = new HashMap<>();
                zp.put("id", zoneId);
                zp.put("warehouseId", loc.wmsCoId());
                zp.put("code", zoneType);
                zp.put("name", loc.area());
                zp.put("type", zoneType);
                out.add(new OntologyRecord("Zone", zp));
            }
            Map<String, Object> lp = new HashMap<>();
            lp.put("id", loc.slotId());
            lp.put("zoneId", zoneId);
            lp.put("code", loc.slotId());
            lp.put("floor", loc.floor() == null ? 1 : loc.floor());
            lp.put("type", "BIN");
            out.add(new OntologyRecord("Location", lp));
        }
        return out;
    }

    @Override
    public List<OntologyRecord> pullSkus(Instant since) {
        return client.querySkus(since).stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", s.skuId());
                    m.put("code", s.skuId());
                    m.put("name", s.name());
                    return new OntologyRecord("Sku", m);
                })
                .toList();
    }

    @Override
    public List<OntologyRecord> pullInventory(Instant since) {
        return client.queryInventory(since).stream()
                .map(inv -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", inv.skuId() + "@" + inv.slotId());
                    m.put("skuId", inv.skuId());
                    m.put("locationId", inv.slotId());
                    m.put("quantity", inv.qty());
                    return new OntologyRecord("Inventory", m);
                })
                .toList();
    }
}
