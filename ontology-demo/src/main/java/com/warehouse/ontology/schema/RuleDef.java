package com.warehouse.ontology.schema;

/**
 * Declarative anomaly detection rule defined in ontology.yml.
 * Evaluated by RuleEngine at runtime — zero hardcoded domain logic.
 */
public record RuleDef(
        String id,
        String description,
        String objectType,
        String severity,
        Condition when,
        Check check,
        String message
) {
    /** Condition on an object's property: property operator value. */
    public record Condition(String property, String operator, Object value) {}

    /** Follow a link type and check target object. */
    public record Check(String follow, Condition condition) {}
}
