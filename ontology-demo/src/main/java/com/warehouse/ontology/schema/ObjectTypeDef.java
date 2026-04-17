package com.warehouse.ontology.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ObjectTypeDef(
        String id,
        String description,
        String primaryKey,
        Map<String, PropertyDef> properties
) {

    public ObjectTypeDef {
        primaryKey = (primaryKey == null || primaryKey.isBlank()) ? "id" : primaryKey;
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
