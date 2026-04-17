package com.warehouse.ontology.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ActionDefinitionParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ActionDefinitionParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, ActionParameterDef> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        Map<String, Object> rawParameters = readMap(json);
        LinkedHashMap<String, ActionParameterDef> parsed = new LinkedHashMap<>();
        rawParameters.forEach((name, value) -> {
            Map<String, Object> parameterDef = asMap(value, "parameter");
            parsed.put(name, new ActionParameterDef(
                    stringValue(parameterDef.get("type")),
                    booleanValue(parameterDef.get("required"))
            ));
        });
        return Collections.unmodifiableMap(parsed);
    }

    public List<String> parsePreconditions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return readList(json).stream().map(String::valueOf).toList();
    }

    public List<ActionMutationDef> parseMutations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        return readList(json).stream()
                .map(item -> {
                    Map<String, Object> mutationDef = asMap(item, "mutation");
                    return new ActionMutationDef(asMap(mutationDef.get("set"), "mutation set"));
                })
                .toList();
    }

    public List<SideEffectDef> parseSideEffects(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        return readList(json).stream()
                .map(item -> {
                    Map<String, Object> effectDef = asMap(item, "side effect");
                    return new SideEffectDef(
                            stringValue(effectDef.get("target")),
                            stringValue(effectDef.get("via")),
                            asMap(effectDef.get("set"), "side effect set"),
                            stringValue(effectDef.get("whenFieldPresent"))
                    );
                })
                .toList();
    }

    private Map<String, Object> readMap(String json) {
        try {
            Map<String, Object> value = objectMapper.readValue(json, MAP_TYPE);
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid action metadata JSON", exception);
        }
    }

    private List<Object> readList(String json) {
        try {
            List<Object> value = objectMapper.readValue(json, LIST_TYPE);
            return value == null ? List.of() : value;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid action metadata JSON", exception);
        }
    }

    private Map<String, Object> asMap(Object value, String label) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Invalid " + label + " definition");
        }

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, rawValue) -> map.put(String.valueOf(key), rawValue));
        return Collections.unmodifiableMap(map);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
