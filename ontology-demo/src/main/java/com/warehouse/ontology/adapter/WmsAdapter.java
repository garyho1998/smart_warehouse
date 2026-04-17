package com.warehouse.ontology.adapter;

import java.time.Instant;
import java.util.List;

public interface WmsAdapter {
    String name();

    List<OntologyRecord> pullWarehouses(Instant since);

    List<OntologyRecord> pullLocations(Instant since);

    List<OntologyRecord> pullSkus(Instant since);

    List<OntologyRecord> pullInventory(Instant since);
}
