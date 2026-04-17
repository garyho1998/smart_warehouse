package com.warehouse.ontology.schema;

public record LinkTypeDef(
        String id,
        String fromType,
        String toType,
        String foreignKey,
        String cardinality,
        String description
) {
}
