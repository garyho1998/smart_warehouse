package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class GenericRepository {

    private final JdbcTemplate jdbcTemplate;
    private final SchemaMetaRepository schemaMetaRepository;
    private final SchemaValueValidator schemaValueValidator;
    private final SqlNameResolver sqlNameResolver;
    private final RowMapperAdapter rowMapperAdapter;

    public GenericRepository(
            JdbcTemplate jdbcTemplate,
            SchemaMetaRepository schemaMetaRepository,
            SchemaValueValidator schemaValueValidator,
            SqlNameResolver sqlNameResolver,
            RowMapperAdapter rowMapperAdapter
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.schemaMetaRepository = schemaMetaRepository;
        this.schemaValueValidator = schemaValueValidator;
        this.sqlNameResolver = sqlNameResolver;
        this.rowMapperAdapter = rowMapperAdapter;
    }

    public Optional<Map<String, Object>> findById(String typeName, String id) {
        ObjectTypeDef typeDef = typeDef(typeName);
        String sql = "SELECT * FROM " + tableName(typeName)
                + " WHERE " + columnName(typeDef.primaryKey()) + " = ?";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, rowMapperAdapter.rowMapper(typeDef), id);
        return rows.stream().findFirst();
    }

    public List<Map<String, Object>> findAll(String typeName) {
        ObjectTypeDef typeDef = typeDef(typeName);
        String sql = "SELECT * FROM " + tableName(typeName)
                + " ORDER BY " + columnName(typeDef.primaryKey());
        return List.copyOf(jdbcTemplate.query(sql, rowMapperAdapter.rowMapper(typeDef)));
    }

    public List<Map<String, Object>> findByProperty(String typeName, String propertyName, Object value) {
        ObjectTypeDef typeDef = typeDef(typeName);
        Object normalizedValue = schemaValueValidator.validateFilterValue(typeDef, propertyName, value);
        String sql = "SELECT * FROM " + tableName(typeName)
                + " WHERE " + columnName(propertyName) + " = ?"
                + " ORDER BY " + columnName(typeDef.primaryKey());
        return List.copyOf(jdbcTemplate.query(sql, rowMapperAdapter.rowMapper(typeDef), normalizedValue));
    }

    public String insert(String typeName, Map<String, Object> data) {
        ObjectTypeDef typeDef = typeDef(typeName);
        LinkedHashMap<String, Object> normalized = schemaValueValidator.validateForInsert(typeDef, data);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Cannot insert empty payload for type " + typeName);
        }

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        normalized.forEach((propertyName, value) -> {
            columns.add(columnName(propertyName));
            values.add(value);
        });

        StringJoiner placeholders = new StringJoiner(", ");
        columns.forEach(ignored -> placeholders.add("?"));

        String sql = "INSERT INTO " + tableName(typeName)
                + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
        jdbcTemplate.update(sql, values.toArray());
        return String.valueOf(normalized.get(typeDef.primaryKey()));
    }

    public void update(String typeName, String id, Map<String, Object> data) {
        ObjectTypeDef typeDef = typeDef(typeName);
        LinkedHashMap<String, Object> normalized = schemaValueValidator.validateForUpdate(typeDef, data);
        if (normalized.isEmpty()) {
            return;
        }

        StringJoiner assignments = new StringJoiner(", ");
        List<Object> values = new ArrayList<>();
        normalized.forEach((propertyName, value) -> {
            assignments.add(columnName(propertyName) + " = ?");
            values.add(value);
        });
        values.add(id);

        String sql = "UPDATE " + tableName(typeName)
                + " SET " + assignments
                + " WHERE " + columnName(typeDef.primaryKey()) + " = ?";
        jdbcTemplate.update(sql, values.toArray());
    }

    private ObjectTypeDef typeDef(String typeName) {
        return schemaMetaRepository.getSchema().requireObjectType(typeName);
    }

    private String tableName(String typeName) {
        return sqlNameResolver.tableName(typeName);
    }

    private String columnName(String propertyName) {
        return sqlNameResolver.columnName(propertyName);
    }
}
