package com.warehouse.ontology.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ActionResult(
        String actionName,
        String objectType,
        String objectId,
        Map<String, Object> updatedObject,
        List<ActionSideEffectResult> sideEffects
) {

    public ActionResult {
        updatedObject = Collections.unmodifiableMap(new LinkedHashMap<>(updatedObject));
        sideEffects = List.copyOf(sideEffects);
    }
}
