package com.warehouse.ontology.adapter.wangdiantong;

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
public class WangdiantongAdapter implements WmsAdapter {

    private static final Map<String, String> ZONE_MAP = Map.of(
            "揀貨區", "PICK",
            "儲存區", "STORAGE",
            "收貨區", "RECEIVE",
            "發貨區", "DOCK",
            "暫存區", "STAGING",
            "品管區", "QC"
    );

    private final WangdiantongClient client;

    public WangdiantongAdapter(WangdiantongClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "wangdiantong";
    }

    @Override
    public List<OntologyRecord> pullWarehouses(Instant since) {
        return client.queryWarehouses(since).stream()
                .map(w -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id", w.warehouseNo());
                    p.put("code", w.warehouseNo());
                    p.put("name", w.warehouseName());
                    p.put("wmsType", "WDT");
                    return new OntologyRecord("Warehouse", p);
                })
                .toList();
    }

    @Override
    public List<OntologyRecord> pullLocations(Instant since) {
        List<OntologyRecord> out = new ArrayList<>();
        Set<String> emittedZoneIds = new HashSet<>();
        for (WdtLocationDto loc : client.queryLocations(since)) {
            String zoneType = ZONE_MAP.getOrDefault(loc.zoneName(), "STORAGE");
            String zoneId = zoneIdFor(loc.warehouseNo(), zoneType);
            if (emittedZoneIds.add(zoneId)) {
                Map<String, Object> zoneProps = new HashMap<>();
                zoneProps.put("id", zoneId);
                zoneProps.put("warehouseId", loc.warehouseNo());
                zoneProps.put("code", zoneType);
                zoneProps.put("name", loc.zoneName());
                zoneProps.put("type", zoneType);
                out.add(new OntologyRecord("Zone", zoneProps));
            }
            Map<String, Object> locProps = new HashMap<>();
            locProps.put("id", loc.binCode());
            locProps.put("zoneId", zoneId);
            locProps.put("code", loc.binCode());
            locProps.put("floor", 1);
            locProps.put("type", "BIN");
            out.add(new OntologyRecord("Location", locProps));
        }
        return out;
    }

    @Override
    public List<OntologyRecord> pullSkus(Instant since) {
        return client.querySkus(since).stream()
                .map(sku -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id", sku.specNo());
                    p.put("code", sku.specNo());
                    p.put("name", sku.goodsName());
                    return new OntologyRecord("Sku", p);
                })
                .toList();
    }

    @Override
    public List<OntologyRecord> pullInventory(Instant since) {
        return client.queryStock(since).stream()
                .map(stk -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("id", stk.specNo() + "@" + stk.binCode());
                    p.put("skuId", stk.specNo());
                    p.put("locationId", stk.binCode());
                    p.put("quantity", stk.stockNum());
                    return new OntologyRecord("Inventory", p);
                })
                .toList();
    }

    private static String zoneIdFor(String warehouseNo, String zoneType) {
        return warehouseNo + "-" + zoneType;
    }
}
