package com.warehouse.ontology.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ActionSideEffectResult(
        String targetType,
        String targetId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState
) {

    public ActionSideEffectResult {
        beforeState = Collections.unmodifiableMap(new LinkedHashMap<>(beforeState));
        afterState = Collections.unmodifiableMap(new LinkedHashMap<>(afterState));
    }
}
