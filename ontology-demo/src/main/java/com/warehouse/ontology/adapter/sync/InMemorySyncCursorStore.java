package com.warehouse.ontology.adapter.sync;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySyncCursorStore implements SyncCursorStore {

    private final ConcurrentMap<String, Instant> cursors = new ConcurrentHashMap<>();

    @Override
    public Instant getLastSync(String adapterName) {
        return cursors.getOrDefault(adapterName, Instant.EPOCH);
    }

    @Override
    public void setLastSync(String adapterName, Instant at) {
        cursors.put(adapterName, at);
    }
}
