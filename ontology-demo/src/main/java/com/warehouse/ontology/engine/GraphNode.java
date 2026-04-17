package com.warehouse.ontology.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GraphNode(
        String key,
        String type,
        String id,
        String label,
        Map<String, Object> properties
) {

    public GraphNode {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
