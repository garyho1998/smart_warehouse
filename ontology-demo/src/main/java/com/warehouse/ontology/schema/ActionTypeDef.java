package com.warehouse.ontology.schema;

public record ActionTypeDef(
        String id,
        String description,
        String objectTypeId,
        String parametersJson,
        String preconditionsJson,
        String mutationsJson,
        String sideEffectsJson,
        boolean audit
) {
}
