package com.warehouse.ontology.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ActionMutationDef(
        Map<String, Object> set
) {

    public ActionMutationDef {
        set = Collections.unmodifiableMap(new LinkedHashMap<>(set));
    }
}
