package com.warehouse.ontology.adapter.sync;

import java.time.Instant;

public interface SyncCursorStore {
    Instant getLastSync(String adapterName);

    void setLastSync(String adapterName, Instant at);
}
