package com.warehouse.ontology.schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record OntologySchema(
        Map<String, ObjectTypeDef> objectTypes,
        Map<String, LinkTypeDef> linkTypes,
        Map<String, ActionTypeDef> actionTypes,
        Map<String, RuleDef> rules,
        Map<String, FunctionDef> functions
) {

    public OntologySchema {
        objectTypes = Collections.unmodifiableMap(new LinkedHashMap<>(objectTypes));
        linkTypes = Collections.unmodifiableMap(new LinkedHashMap<>(linkTypes));
        actionTypes = Collections.unmodifiableMap(new LinkedHashMap<>(actionTypes));
        rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
        functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
    }

    public ObjectTypeDef requireObjectType(String typeName) {
        ObjectTypeDef typeDef = objectTypes.get(typeName);
        if (typeDef == null) {
            throw new IllegalArgumentException("Unknown object type: " + typeName);
        }
        return typeDef;
    }
}
