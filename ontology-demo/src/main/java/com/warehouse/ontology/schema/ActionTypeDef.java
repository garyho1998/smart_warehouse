package com.warehouse.ontology.schema;

public record ActionTypeDef(
        String id,
        String description,
        String objectTypeId,
        String parametersJson,
        String preconditionsJson,
        String mutationsJson,
        String sideEffectsJson,
        boolean audit,
        String mode
) {

    public String mode() {
        return mode == null || mode.isBlank() ? "UPDATE" : mode;
    }
}
