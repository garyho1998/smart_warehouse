package com.warehouse.ontology.adapter.sync;

import com.warehouse.ontology.adapter.OntologyRecord;
import com.warehouse.ontology.adapter.WmsAdapter;
import com.warehouse.ontology.engine.GenericRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WmsSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(WmsSyncScheduler.class);

    private final List<WmsAdapter> adapters;
    private final GenericRepository repo;
    private final SyncCursorStore cursor;

    public WmsSyncScheduler(List<WmsAdapter> adapters, GenericRepository repo, SyncCursorStore cursor) {
        this.adapters = adapters;
        this.repo = repo;
        this.cursor = cursor;
    }

    @Scheduled(fixedRateString = "${wms.sync.interval-ms:30000}",
               initialDelayString = "${wms.sync.initial-delay-ms:5000}")
    public void runOnce() {
        for (WmsAdapter adapter : adapters) {
            syncOne(adapter);
        }
    }

    private void syncOne(WmsAdapter adapter) {
        Instant since = cursor.getLastSync(adapter.name());
        Instant syncStart = Instant.now();
        try {
            List<OntologyRecord> all = List.of(
                            adapter.pullWarehouses(since),
                            adapter.pullLocations(since),
                            adapter.pullSkus(since),
                            adapter.pullInventory(since))
                    .stream().flatMap(List::stream).toList();

            for (OntologyRecord rec : all) {
                try {
                    repo.upsert(rec.type(), rec.properties());
                } catch (RuntimeException e) {
                    log.warn("Failed to upsert {} for adapter {}: {}", rec.type(), adapter.name(), e.getMessage());
                }
            }
            cursor.setLastSync(adapter.name(), syncStart);
            log.info("Adapter {} synced {} records (cursor {} -> {})",
                    adapter.name(), all.size(), since, syncStart);
        } catch (RuntimeException e) {
            log.error("Sync failed for adapter {}: {}", adapter.name(), e.getMessage(), e);
        }
    }
}
