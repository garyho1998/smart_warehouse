package com.warehouse.ontology.api;

import com.warehouse.ontology.adapter.WmsAdapter;
import com.warehouse.ontology.adapter.sync.SyncCursorStore;
import com.warehouse.ontology.adapter.sync.WmsSyncScheduler;
import com.warehouse.ontology.engine.GenericRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sources")
public class SourcesController {

    private static final List<String> OBJECT_TYPES = List.of("Warehouse", "Zone", "Location", "Sku", "Inventory");

    private final List<WmsAdapter> adapters;
    private final SyncCursorStore cursorStore;
    private final GenericRepository repo;
    private final WmsSyncScheduler scheduler;

    public SourcesController(List<WmsAdapter> adapters,
                             SyncCursorStore cursorStore,
                             GenericRepository repo,
                             WmsSyncScheduler scheduler) {
        this.adapters = adapters;
        this.cursorStore = cursorStore;
        this.repo = repo;
        this.scheduler = scheduler;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Map<String, Long> counts = new HashMap<>();
        for (String type : OBJECT_TYPES) {
            try {
                counts.put(type, repo.count(type));
            } catch (RuntimeException e) {
                counts.put(type, 0L);
            }
        }
        return adapters.stream().map(a -> {
            Map<String, Object> src = new HashMap<>();
            src.put("name", a.name());
            Instant lastSync = cursorStore.getLastSync(a.name());
            src.put("lastSyncAt", Instant.EPOCH.equals(lastSync) ? null : lastSync.toString());
            src.put("counts", counts);
            return src;
        }).toList();
    }

    @PostMapping("/{name}/sync")
    public Map<String, Object> sync(@PathVariable String name) {
        scheduler.runOnce();
        Instant last = cursorStore.getLastSync(name);
        return Map.of("name", name, "syncedAt", last.toString());
    }
}
