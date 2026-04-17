package com.warehouse.ontology.schema;

import java.util.Collections;
import java.util.List;

public record PropertyDef(
        Long id,
        String objectTypeId,
        String name,
        String type,
        boolean required,
        boolean uniqueCol,
        String defaultValue,
        List<String> enumValues
) {

    public PropertyDef {
        enumValues = enumValues == null ? List.of() : Collections.unmodifiableList(List.copyOf(enumValues));
    }
}
