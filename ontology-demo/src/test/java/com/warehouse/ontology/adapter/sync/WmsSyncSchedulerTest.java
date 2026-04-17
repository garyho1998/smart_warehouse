package com.warehouse.ontology.adapter.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.warehouse.ontology.adapter.OntologyRecord;
import com.warehouse.ontology.adapter.WmsAdapter;
import com.warehouse.ontology.engine.GenericRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WmsSyncSchedulerTest {

    @Test
    void second_sync_uses_cursor_from_first() throws InterruptedException {
        WmsAdapter adapter = mock(WmsAdapter.class);
        when(adapter.name()).thenReturn("test");
        when(adapter.pullWarehouses(any())).thenReturn(List.of());
        when(adapter.pullLocations(any())).thenReturn(List.of());
        when(adapter.pullSkus(any())).thenReturn(List.of());
        when(adapter.pullInventory(any())).thenReturn(List.of());

        GenericRepository repo = mock(GenericRepository.class);
        SyncCursorStore cursor = new InMemorySyncCursorStore();
        WmsSyncScheduler scheduler = new WmsSyncScheduler(List.of(adapter), repo, cursor);

        scheduler.runOnce();
        Instant firstSync = cursor.getLastSync("test");
        assertThat(firstSync).isAfter(Instant.EPOCH);

        Thread.sleep(10);
        scheduler.runOnce();

        verify(adapter).pullInventory(Instant.EPOCH);
        verify(adapter).pullInventory(firstSync);
    }

    @Test
    void empty_adapter_list_is_noop() {
        GenericRepository repo = mock(GenericRepository.class);
        SyncCursorStore cursor = new InMemorySyncCursorStore();
        WmsSyncScheduler scheduler = new WmsSyncScheduler(List.of(), repo, cursor);

        scheduler.runOnce();

        verify(repo, never()).upsert(any(), any());
    }

    @Test
    void upserts_each_record_returned_by_adapter() {
        WmsAdapter adapter = mock(WmsAdapter.class);
        when(adapter.name()).thenReturn("test");
        OntologyRecord wh = new OntologyRecord("Warehouse", Map.of("id", "W1"));
        when(adapter.pullWarehouses(any())).thenReturn(List.of(wh));
        when(adapter.pullLocations(any())).thenReturn(List.of());
        when(adapter.pullSkus(any())).thenReturn(List.of());
        when(adapter.pullInventory(any())).thenReturn(List.of());

        GenericRepository repo = mock(GenericRepository.class);
        SyncCursorStore cursor = new InMemorySyncCursorStore();
        WmsSyncScheduler scheduler = new WmsSyncScheduler(List.of(adapter), repo, cursor);

        scheduler.runOnce();

        verify(repo).upsert("Warehouse", Map.of("id", "W1"));
    }
}
