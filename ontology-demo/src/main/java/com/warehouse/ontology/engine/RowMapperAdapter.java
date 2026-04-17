package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.ObjectTypeDef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class RowMapperAdapter {

    private final SqlNameResolver sqlNameResolver;

    public RowMapperAdapter(SqlNameResolver sqlNameResolver) {
        this.sqlNameResolver = sqlNameResolver;
    }

    public RowMapper<Map<String, Object>> rowMapper(ObjectTypeDef typeDef) {
        return (resultSet, rowNum) -> {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            typeDef.properties().values().forEach(propertyDef -> row.put(
                    propertyDef.name(),
                    getObject(resultSet, sqlNameResolver.columnName(propertyDef.name()))
            ));
            return Collections.unmodifiableMap(row);
        };
    }

    private Object getObject(java.sql.ResultSet resultSet, String columnName) {
        try {
            return resultSet.getObject(columnName);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Unable to read column '" + columnName + "'", exception);
        }
    }
}
