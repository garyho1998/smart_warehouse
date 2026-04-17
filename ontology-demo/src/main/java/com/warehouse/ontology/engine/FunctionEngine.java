package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.FunctionDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.RuleDef;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Computes derived properties defined as functions in ontology.yml.
 * Enriches objects with calculated fields (e.g. robotHealthScore: CRITICAL).
 */
@Component
public class FunctionEngine {

    /**
     * Compute all derived properties for an object of the given type.
     * Returns a map of functionName → computed value.
     */
    public Map<String, Object> computeDerived(
            String objectType, Map<String, Object> object, OntologySchema schema) {
        Map<String, Object> derived = new LinkedHashMap<>();

        for (FunctionDef func : schema.functions().values()) {
            if (!func.objectType().equals(objectType)) {
                continue;
            }
            String value = evaluate(func, object);
            if (value != null) {
                derived.put(func.id(), value);
            }
        }
        return derived;
    }

    private String evaluate(FunctionDef func, Map<String, Object> object) {
        for (FunctionDef.Case c : func.cases()) {
            if (c.when() == null) {
                // Default case (no condition)
                return c.then();
            }
            if (matchCondition(c.when(), object)) {
                return c.then();
            }
        }
        return null;
    }

    private boolean matchCondition(FunctionDef.Condition condition, Map<String, Object> object) {
        // Reuse the same comparison logic as RuleEngine
        var ruleCondition = new RuleDef.Condition(
                condition.property(), condition.operator(), condition.value());
        return RuleEngine.matchCondition(ruleCondition, object);
    }
}
