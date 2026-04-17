package com.warehouse.ontology.engine;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ActionValueResolver {

    public LinkedHashMap<String, Object> resolveSet(Map<String, Object> rawSet) {
        return resolveSet(rawSet, Map.of());
    }

    public LinkedHashMap<String, Object> resolveSet(Map<String, Object> rawSet, Map<String, Object> context) {
        return resolveSet(rawSet, context, Map.of());
    }

    public LinkedHashMap<String, Object> resolveSet(
            Map<String, Object> rawSet, Map<String, Object> context, Map<String, Object> target
    ) {
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
        rawSet.forEach((key, value) -> resolved.put(key, resolveValue(value, context, target)));
        return resolved;
    }

    private Object resolveValue(Object value, Map<String, Object> context, Map<String, Object> target) {
        if (value instanceof String stringValue) {
            if ("NOW".equalsIgnoreCase(stringValue)) {
                return Timestamp.from(Instant.now());
            }
            if (stringValue.startsWith("$param.")) {
                return context.get(stringValue.substring(7));
            }
            if (stringValue.startsWith("$target.")) {
                return target.get(stringValue.substring(8));
            }
        }
        return value;
    }
}
