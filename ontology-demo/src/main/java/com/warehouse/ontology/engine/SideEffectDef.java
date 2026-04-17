package com.warehouse.ontology.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record SideEffectDef(
        String target,
        String via,
        Map<String, Object> set,
        String whenFieldPresent
) {

    public SideEffectDef {
        set = Collections.unmodifiableMap(new LinkedHashMap<>(set));
    }
}
