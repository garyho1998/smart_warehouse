package com.warehouse.ontology.engine;

import org.springframework.stereotype.Component;

@Component
public class SqlNameResolver {

    public String tableName(String typeName) {
        return snakeCase(typeName);
    }

    public String columnName(String propertyName) {
        return snakeCase(propertyName);
    }

    private String snakeCase(String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isUpperCase(character) && index > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(character));
        }
        return builder.toString();
    }
}
