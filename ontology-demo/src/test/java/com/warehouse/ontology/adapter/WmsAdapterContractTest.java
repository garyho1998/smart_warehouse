package com.warehouse.ontology.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WmsAdapterContractTest {

    @Test
    void adapter_exposes_name_and_incremental_pull_methods() {
        WmsAdapter stub = new WmsAdapter() {
            @Override
            public String name() {
                return "stub";
            }

            @Override
            public List<OntologyRecord> pullWarehouses(Instant since) {
                return List.of();
            }

            @Override
            public List<OntologyRecord> pullLocations(Instant since) {
                return List.of();
            }

            @Override
            public List<OntologyRecord> pullSkus(Instant since) {
                return List.of();
            }

            @Override
            public List<OntologyRecord> pullInventory(Instant since) {
                return List.of();
            }
        };

        assertThat(stub.name()).isEqualTo("stub");
        assertThat(stub.pullWarehouses(Instant.EPOCH)).isEmpty();
        assertThat(stub.pullLocations(Instant.EPOCH)).isEmpty();
        assertThat(stub.pullSkus(Instant.EPOCH)).isEmpty();
        assertThat(stub.pullInventory(Instant.EPOCH)).isEmpty();
    }
}
