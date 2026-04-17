package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.LinkTypeDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.RuleDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generic rule evaluator — reads declarative rules from OntologySchema,
 * evaluates them against objects + linked objects. Zero hardcoded domain logic.
 */
@Component
public class RuleEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(target\\.)?([^}]+)}");

    private final GenericRepository genericRepository;

    public RuleEngine(GenericRepository genericRepository) {
        this.genericRepository = genericRepository;
    }

    /**
     * Evaluate all matching rules for the given object. Returns insights for rules
     * whose conditions are satisfied.
     */
    public List<GraphInsight> evaluate(
            String objectType, Map<String, Object> object, OntologySchema schema) {
        List<GraphInsight> insights = new ArrayList<>();

        for (RuleDef rule : schema.rules().values()) {
            if (!rule.objectType().equals(objectType)) {
                continue;
            }
            // Check trigger condition on the source object
            if (rule.when() != null && !matchCondition(rule.when(), object)) {
                continue;
            }

            if (rule.check() != null) {
                // Follow link → check target object
                resolveTarget(object, rule.check().follow(), schema).ifPresent(target -> {
                    if (rule.check().condition() == null
                            || matchCondition(rule.check().condition(), target)) {
                        insights.add(new GraphInsight(
                                rule.severity(),
                                interpolate(rule.message(), object, target)
                        ));
                    }
                });
            } else {
                // Simple condition-only rule (no link traversal)
                insights.add(new GraphInsight(
                        rule.severity(),
                        interpolate(rule.message(), object, null)
                ));
            }
        }
        return insights;
    }

    private Optional<Map<String, Object>> resolveTarget(
            Map<String, Object> sourceObject, String linkTypeName, OntologySchema schema) {
        LinkTypeDef link = schema.linkTypes().get(linkTypeName);
        if (link == null) {
            return Optional.empty();
        }

        // The source object has the FK → fetch the target by FK value
        Object fkValue = sourceObject.get(link.foreignKey());
        if (fkValue == null || String.valueOf(fkValue).isBlank()) {
            return Optional.empty();
        }
        return genericRepository.findById(link.toType(), String.valueOf(fkValue));
    }

    static boolean matchCondition(RuleDef.Condition condition, Map<String, Object> object) {
        Object actual = object.get(condition.property());
        Object expected = condition.value();

        return switch (condition.operator()) {
            case "eq" -> equal(actual, expected);
            case "neq" -> !equal(actual, expected);
            case "lte" -> compare(actual, expected) <= 0;
            case "gte" -> compare(actual, expected) >= 0;
            case "lt" -> compare(actual, expected) < 0;
            case "gt" -> compare(actual, expected) > 0;
            default -> false;
        };
    }

    private static boolean equal(Object actual, Object expected) {
        if (actual == null && expected == null) return true;
        if (actual == null || expected == null) return false;
        // Compare as strings for robustness (DB may return different types)
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private static int compare(Object actual, Object expected) {
        if (actual == null) return -1;
        try {
            double a = actual instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(actual));
            double e = expected instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(expected));
            return Double.compare(a, e);
        } catch (NumberFormatException ex) {
            return String.valueOf(actual).compareTo(String.valueOf(expected));
        }
    }

    static String interpolate(String template, Map<String, Object> source, Map<String, Object> target) {
        if (template == null) return "";
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String prefix = matcher.group(1);           // "target." or null
            String property = matcher.group(2);
            Map<String, Object> context = "target.".equals(prefix) && target != null ? target : source;
            Object value = context != null ? context.get(property) : null;
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? String.valueOf(value) : "?"));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
