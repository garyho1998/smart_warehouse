package com.warehouse.ontology.schema;

import java.util.List;

/**
 * Derived property function defined in ontology.yml.
 * Computed at query time by FunctionEngine — enriches GraphNode properties.
 */
public record FunctionDef(
        String id,
        String objectType,
        String returnType,
        List<Case> cases
) {
    /** A single case in a case-when-then chain. when==null means default. */
    public record Case(Condition when, String then) {}

    /** Condition on an object's property. */
    public record Condition(String property, String operator, Object value) {}
}
