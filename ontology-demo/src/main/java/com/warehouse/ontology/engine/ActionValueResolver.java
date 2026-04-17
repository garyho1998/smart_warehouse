package com.warehouse.ontology.engine;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ActionValueResolver {

    public LinkedHashMap<String, Object> resolveSet(Map<String, Object> rawSet) {
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
        rawSet.forEach((key, value) -> resolved.put(key, resolveValue(value)));
        return resolved;
    }

    private Object resolveValue(Object value) {
        if (value instanceof String stringValue && "NOW".equalsIgnoreCase(stringValue)) {
            return Timestamp.from(Instant.now());
        }
        return value;
    }
}
