package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.LinkTypeDef;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.PropertyDef;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataTableSyncer {

    private final JdbcTemplate jdbcTemplate;

    public DataTableSyncer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void syncAll(OntologySchema schema) {
        schema.objectTypes().forEach(this::syncTable);
        schema.linkTypes().values().forEach(linkTypeDef -> ensureForeignKey(schema, linkTypeDef));
    }

    public void syncTable(String typeName, ObjectTypeDef def) {
        String tableName = tableName(typeName);
        if (!tableExists(tableName)) {
            createTable(tableName, def);
        } else {
            addMissingColumns(tableName, def);
        }
        ensureUniqueIndexes(tableName, def);
    }

    private void createTable(String tableName, ObjectTypeDef def) {
        String primaryKeyColumn = columnName(def.primaryKey());
        if (!def.properties().containsKey(def.primaryKey())) {
            throw new IllegalStateException("Primary key property is missing for type " + def.id());
        }

        StringJoiner joiner = new StringJoiner(", ");
        def.properties().values().forEach(propertyDef -> joiner.add(columnDefinition(propertyDef)));
        joiner.add("PRIMARY KEY (" + primaryKeyColumn + ")");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (" + joiner + ")");
    }

    private void addMissingColumns(String tableName, ObjectTypeDef def) {
        Set<String> existingColumns = existingColumns(tableName);
        def.properties().values().forEach(propertyDef -> {
            String columnName = columnName(propertyDef.name());
            if (!existingColumns.contains(columnName)) {
                jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnDefinition(propertyDef));
            }
        });
    }

    private void ensureUniqueIndexes(String tableName, ObjectTypeDef def) {
        def.properties().values().stream()
                .filter(PropertyDef::uniqueCol)
                .forEach(propertyDef -> {
                    String columnName = columnName(propertyDef.name());
                    String indexName = "uk_" + tableName + "_" + columnName;
                    if (!indexExists(tableName, indexName)) {
                        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS " + indexName
                                + " ON " + tableName + " (" + columnName + ")");
                    }
                });
    }

    private void ensureForeignKey(OntologySchema schema, LinkTypeDef linkTypeDef) {
        ForeignKeyBinding foreignKeyBinding = resolveForeignKeyBinding(schema, linkTypeDef);
        if (foreignKeyBinding == null) {
            return;
        }

        String sourceTable = tableName(foreignKeyBinding.ownerType());
        String targetTable = tableName(foreignKeyBinding.referenceType());
        String sourceColumn = columnName(foreignKeyBinding.foreignKey());
        String targetColumn = columnName(schema.requireObjectType(foreignKeyBinding.referenceType()).primaryKey());

        if (!tableExists(sourceTable) || !tableExists(targetTable)) {
            return;
        }

        if (!existingColumns(sourceTable).contains(sourceColumn) || !existingColumns(targetTable).contains(targetColumn)) {
            return;
        }

        String indexName = "idx_" + sourceTable + "_" + sourceColumn;
        if (!indexExists(sourceTable, indexName)) {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + indexName
                    + " ON " + sourceTable + " (" + sourceColumn + ")");
        }

        String constraintName = "fk_" + snakeCase(linkTypeDef.id());
        if (!constraintExists(sourceTable, constraintName)) {
            jdbcTemplate.execute("ALTER TABLE " + sourceTable
                    + " ADD CONSTRAINT " + constraintName
                    + " FOREIGN KEY (" + sourceColumn + ") REFERENCES "
                    + targetTable + " (" + targetColumn + ")");
        }
    }

    private ForeignKeyBinding resolveForeignKeyBinding(OntologySchema schema, LinkTypeDef linkTypeDef) {
        ObjectTypeDef fromTypeDef = schema.requireObjectType(linkTypeDef.fromType());
        ObjectTypeDef toTypeDef = schema.requireObjectType(linkTypeDef.toType());
        boolean fromOwnsForeignKey = fromTypeDef.properties().containsKey(linkTypeDef.foreignKey());
        boolean toOwnsForeignKey = toTypeDef.properties().containsKey(linkTypeDef.foreignKey());

        if (fromOwnsForeignKey == toOwnsForeignKey) {
            return null;
        }

        if (fromOwnsForeignKey) {
            return new ForeignKeyBinding(linkTypeDef.fromType(), linkTypeDef.toType(), linkTypeDef.foreignKey());
        }
        return new ForeignKeyBinding(linkTypeDef.toType(), linkTypeDef.fromType(), linkTypeDef.foreignKey());
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = ?",
                Integer.class,
                tableName.toLowerCase(Locale.ROOT)
        );
        return count != null && count > 0;
    }

    private Set<String> existingColumns(String tableName) {
        return new LinkedHashSet<>(jdbcTemplate.query(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = ?",
                (rs, rowNum) -> rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT),
                tableName.toLowerCase(Locale.ROOT)
        ));
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE LOWER(TABLE_NAME) = ? AND LOWER(INDEX_NAME) = ?",
                Integer.class,
                tableName.toLowerCase(Locale.ROOT),
                indexName.toLowerCase(Locale.ROOT)
        );
        return count != null && count > 0;
    }

    private boolean constraintExists(String tableName, String constraintName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE LOWER(TABLE_NAME) = ? AND LOWER(CONSTRAINT_NAME) = ?",
                Integer.class,
                tableName.toLowerCase(Locale.ROOT),
                constraintName.toLowerCase(Locale.ROOT)
        );
        return count != null && count > 0;
    }

    private String columnDefinition(PropertyDef propertyDef) {
        StringBuilder builder = new StringBuilder();
        builder.append(columnName(propertyDef.name())).append(' ').append(sqlType(propertyDef.type()));
        if (propertyDef.required()) {
            builder.append(" NOT NULL");
        }
        String defaultClause = defaultClause(propertyDef);
        if (defaultClause != null) {
            builder.append(" DEFAULT ").append(defaultClause);
        }
        return builder.toString();
    }

    private String sqlType(String ontologyType) {
        return switch (ontologyType) {
            case "string" -> "VARCHAR(255)";
            case "integer" -> "INTEGER";
            case "decimal" -> "DECIMAL(15,4)";
            case "boolean" -> "BOOLEAN";
            case "enum" -> "VARCHAR(50)";
            case "timestamp" -> "TIMESTAMP";
            default -> throw new IllegalArgumentException("Unsupported ontology property type: " + ontologyType);
        };
    }

    private String defaultClause(PropertyDef propertyDef) {
        if (propertyDef.defaultValue() == null || propertyDef.defaultValue().isBlank()) {
            return null;
        }

        return switch (propertyDef.type()) {
            case "integer", "decimal" -> propertyDef.defaultValue();
            case "boolean" -> String.valueOf(Boolean.parseBoolean(propertyDef.defaultValue())).toUpperCase(Locale.ROOT);
            case "timestamp" -> "NOW".equalsIgnoreCase(propertyDef.defaultValue())
                    ? "CURRENT_TIMESTAMP"
                    : "'" + propertyDef.defaultValue() + "'";
            default -> "'" + propertyDef.defaultValue().replace("'", "''") + "'";
        };
    }

    private String tableName(String typeName) {
        return snakeCase(typeName);
    }

    private String columnName(String propertyName) {
        return snakeCase(propertyName);
    }

    private String snakeCase(String value) {
        return value.chars()
                .mapToObj(character -> String.valueOf((char) character))
                .collect(Collectors.collectingAndThen(Collectors.toList(), parts -> {
                    StringBuilder builder = new StringBuilder();
                    for (int index = 0; index < parts.size(); index++) {
                        char character = parts.get(index).charAt(0);
                        if (Character.isUpperCase(character) && index > 0) {
                            builder.append('_');
                        }
                        builder.append(Character.toLowerCase(character));
                    }
                    return builder.toString();
                }));
    }

    private record ForeignKeyBinding(
            String ownerType,
            String referenceType,
            String foreignKey
    ) {
    }
}
