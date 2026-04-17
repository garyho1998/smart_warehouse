package com.warehouse.ontology.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.ontology.engine.ActionDefinitionParser;
import com.warehouse.ontology.engine.ActionMutationDef;
import com.warehouse.ontology.engine.DataTableSyncer;
import com.warehouse.ontology.engine.SideEffectDef;
import com.warehouse.ontology.schema.ActionTypeDef;
import com.warehouse.ontology.schema.LinkTypeDef;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.PropertyDef;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import com.warehouse.ontology.support.NotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchemaService {

    private static final Set<String> SUPPORTED_PROPERTY_TYPES =
            Set.of("string", "integer", "decimal", "boolean", "enum", "timestamp");
    private static final Set<String> SUPPORTED_CARDINALITIES = Set.of("one_to_many", "many_to_one");

    private final SchemaMetaRepository schemaMetaRepository;
    private final DataTableSyncer dataTableSyncer;
    private final ActionDefinitionParser actionDefinitionParser;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SchemaService(
            SchemaMetaRepository schemaMetaRepository,
            DataTableSyncer dataTableSyncer,
            ActionDefinitionParser actionDefinitionParser,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.schemaMetaRepository = schemaMetaRepository;
        this.dataTableSyncer = dataTableSyncer;
        this.actionDefinitionParser = actionDefinitionParser;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ObjectTypeDef createType(ObjectTypeDef def, List<PropertyDef> properties) {
        List<PropertyDef> normalizedProperties = normalizeProperties(def.id(), properties);
        validateNewType(def, normalizedProperties);

        ObjectTypeDef objectTypeDef = new ObjectTypeDef(def.id(), def.description(), def.primaryKey(), Map.of());
        schemaMetaRepository.insertObjectType(objectTypeDef);
        recordSchemaInsert(
                "object_type_def",
                objectTypeDef.id(),
                mapOf("id", objectTypeDef.id(), "description", objectTypeDef.description(), "primaryKey", objectTypeDef.primaryKey())
        );

        for (PropertyDef propertyDef : normalizedProperties) {
            schemaMetaRepository.insertProperty(propertyDef);
            recordSchemaInsert(
                    "property_def",
                    objectTypeDef.id() + ":" + propertyDef.name(),
                    mapOf(
                            "objectTypeId", propertyDef.objectTypeId(),
                            "name", propertyDef.name(),
                            "type", propertyDef.type(),
                            "required", propertyDef.required(),
                            "uniqueCol", propertyDef.uniqueCol(),
                            "defaultValue", propertyDef.defaultValue(),
                            "enumValues", propertyDef.enumValues()
                    )
            );
        }

        schemaMetaRepository.invalidateCache();
        ObjectTypeDef created = schemaMetaRepository.getSchema().requireObjectType(objectTypeDef.id());
        dataTableSyncer.syncTable(created.id(), created);
        return created;
    }

    @Transactional
    public PropertyDef addProperty(String typeName, PropertyDef propertyDef) {
        OntologySchema schema = schemaMetaRepository.getSchema();
        ObjectTypeDef existingType = schema.objectTypes().get(typeName);
        if (existingType == null) {
            throw new NotFoundException("Unknown object type: " + typeName);
        }
        if (existingType.properties().containsKey(propertyDef.name())) {
            throw new IllegalArgumentException("Property already exists: " + typeName + "." + propertyDef.name());
        }
        if (existingType.primaryKey().equals(propertyDef.name())) {
            throw new IllegalArgumentException("Primary key property already exists for type " + typeName);
        }

        PropertyDef normalizedProperty = normalizeProperty(typeName, propertyDef);
        validateProperty(normalizedProperty);
        schemaMetaRepository.insertProperty(normalizedProperty);
        recordSchemaInsert(
                "property_def",
                typeName + ":" + normalizedProperty.name(),
                mapOf(
                        "objectTypeId", normalizedProperty.objectTypeId(),
                        "name", normalizedProperty.name(),
                        "type", normalizedProperty.type(),
                        "required", normalizedProperty.required(),
                        "uniqueCol", normalizedProperty.uniqueCol(),
                        "defaultValue", normalizedProperty.defaultValue(),
                        "enumValues", normalizedProperty.enumValues()
                )
        );

        schemaMetaRepository.invalidateCache();
        ObjectTypeDef updatedType = schemaMetaRepository.getSchema().requireObjectType(typeName);
        dataTableSyncer.syncTable(typeName, updatedType);
        return updatedType.properties().get(normalizedProperty.name());
    }

    @Transactional
    public LinkTypeDef createLinkType(LinkTypeDef def) {
        validateNewLinkType(def);
        schemaMetaRepository.insertLinkType(def);
        recordSchemaInsert(
                "link_type_def",
                def.id(),
                mapOf(
                        "id", def.id(),
                        "fromType", def.fromType(),
                        "toType", def.toType(),
                        "foreignKey", def.foreignKey(),
                        "cardinality", def.cardinality(),
                        "description", def.description()
                )
        );

        schemaMetaRepository.invalidateCache();
        dataTableSyncer.syncAll(schemaMetaRepository.getSchema());
        return schemaMetaRepository.getSchema().linkTypes().get(def.id());
    }

    @Transactional
    public ActionTypeDef createActionType(ActionTypeDef def) {
        validateNewActionType(def);
        schemaMetaRepository.insertActionType(def);
        recordSchemaInsert(
                "action_type_def",
                def.id(),
                mapOf(
                        "id", def.id(),
                        "description", def.description(),
                        "objectTypeId", def.objectTypeId(),
                        "parameters", parseJson(def.parametersJson()),
                        "preconditions", parseJson(def.preconditionsJson()),
                        "mutations", parseJson(def.mutationsJson()),
                        "sideEffects", parseJson(def.sideEffectsJson()),
                        "audit", def.audit()
                )
        );

        schemaMetaRepository.invalidateCache();
        return schemaMetaRepository.getSchema().actionTypes().get(def.id());
    }

    public List<Map<String, Object>> getHistory(String tableName) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, table_name, record_id, operation, old_value, new_value, changed_at FROM schema_history"
        );
        List<Object> args = new ArrayList<>();
        if (tableName != null && !tableName.isBlank()) {
            sql.append(" WHERE table_name = ?");
            args.add(tableName);
        }
        sql.append(" ORDER BY id");

        return jdbcTemplate.query(sql.toString(), (resultSet, rowNum) -> historyRow(resultSet), args.toArray());
    }

    private void validateNewType(ObjectTypeDef def, List<PropertyDef> properties) {
        if (def.id() == null || def.id().isBlank()) {
            throw new IllegalArgumentException("Object type id is required");
        }
        if (schemaMetaRepository.getSchema().objectTypes().containsKey(def.id())) {
            throw new IllegalArgumentException("Object type already exists: " + def.id());
        }
        if (properties.isEmpty()) {
            throw new IllegalArgumentException("Object type must define at least one property");
        }

        Set<String> propertyNames = new LinkedHashSet<>();
        boolean hasPrimaryKey = false;
        for (PropertyDef propertyDef : properties) {
            validateProperty(propertyDef);
            if (!propertyNames.add(propertyDef.name())) {
                throw new IllegalArgumentException("Duplicate property name: " + propertyDef.name());
            }
            if (def.primaryKey().equals(propertyDef.name())) {
                hasPrimaryKey = true;
                if (!propertyDef.required()) {
                    throw new IllegalArgumentException("Primary key property must be required");
                }
            }
        }

        if (!hasPrimaryKey) {
            throw new IllegalArgumentException("Primary key property '" + def.primaryKey() + "' is missing");
        }
    }

    private void validateProperty(PropertyDef propertyDef) {
        if (propertyDef.name() == null || propertyDef.name().isBlank()) {
            throw new IllegalArgumentException("Property name is required");
        }
        if (propertyDef.type() == null || !SUPPORTED_PROPERTY_TYPES.contains(propertyDef.type())) {
            throw new IllegalArgumentException("Unsupported property type: " + propertyDef.type());
        }
        if ("enum".equals(propertyDef.type()) && propertyDef.enumValues().isEmpty()) {
            throw new IllegalArgumentException("Enum property must define enumValues");
        }
    }

    private void validateNewLinkType(LinkTypeDef def) {
        if (def.id() == null || def.id().isBlank()) {
            throw new IllegalArgumentException("Link type id is required");
        }
        if (schemaMetaRepository.getSchema().linkTypes().containsKey(def.id())) {
            throw new IllegalArgumentException("Link type already exists: " + def.id());
        }
        if (!SUPPORTED_CARDINALITIES.contains(def.cardinality())) {
            throw new IllegalArgumentException("Unsupported link cardinality: " + def.cardinality());
        }

        OntologySchema schema = schemaMetaRepository.getSchema();
        ObjectTypeDef fromType = schema.requireObjectType(def.fromType());
        ObjectTypeDef toType = schema.requireObjectType(def.toType());
        boolean fromOwnsForeignKey = fromType.properties().containsKey(def.foreignKey());
        boolean toOwnsForeignKey = toType.properties().containsKey(def.foreignKey());
        if (fromOwnsForeignKey == toOwnsForeignKey) {
            throw new IllegalArgumentException(
                    "Link foreignKey must exist on exactly one side: " + def.foreignKey()
            );
        }
    }

    private void validateNewActionType(ActionTypeDef def) {
        if (def.id() == null || def.id().isBlank()) {
            throw new IllegalArgumentException("Action type id is required");
        }
        if (schemaMetaRepository.getSchema().actionTypes().containsKey(def.id())) {
            throw new IllegalArgumentException("Action type already exists: " + def.id());
        }

        OntologySchema schema = schemaMetaRepository.getSchema();
        ObjectTypeDef objectTypeDef = schema.requireObjectType(def.objectTypeId());
        actionDefinitionParser.parseParameters(def.parametersJson());
        for (String precondition : actionDefinitionParser.parsePreconditions(def.preconditionsJson())) {
            String propertyName = precondition.split("\\s+", 2)[0];
            if (!objectTypeDef.properties().containsKey(propertyName)) {
                throw new IllegalArgumentException("Unknown action precondition property: " + propertyName);
            }
        }
        for (ActionMutationDef mutationDef : actionDefinitionParser.parseMutations(def.mutationsJson())) {
            mutationDef.set().keySet().forEach(propertyName -> {
                if (!objectTypeDef.properties().containsKey(propertyName)) {
                    throw new IllegalArgumentException("Unknown action mutation property: " + propertyName);
                }
            });
        }
        for (SideEffectDef sideEffectDef : actionDefinitionParser.parseSideEffects(def.sideEffectsJson())) {
            if (!objectTypeDef.properties().containsKey(sideEffectDef.via())) {
                throw new IllegalArgumentException("Unknown action side effect property: " + sideEffectDef.via());
            }
            ObjectTypeDef targetType = schema.requireObjectType(sideEffectDef.target());
            sideEffectDef.set().keySet().forEach(propertyName -> {
                if (!targetType.properties().containsKey(propertyName)) {
                    throw new IllegalArgumentException("Unknown side effect target property: " + propertyName);
                }
            });
        }
    }

    private List<PropertyDef> normalizeProperties(String typeName, List<PropertyDef> properties) {
        if (properties == null) {
            return List.of();
        }
        return properties.stream().map(propertyDef -> normalizeProperty(typeName, propertyDef)).toList();
    }

    private PropertyDef normalizeProperty(String typeName, PropertyDef propertyDef) {
        return new PropertyDef(
                null,
                typeName,
                propertyDef.name(),
                propertyDef.type(),
                propertyDef.required(),
                propertyDef.uniqueCol(),
                propertyDef.defaultValue(),
                propertyDef.enumValues()
        );
    }

    private void recordSchemaInsert(String tableName, String recordId, Object newValue) {
        schemaMetaRepository.recordSchemaChange(tableName, recordId, "INSERT", null, toJson(newValue));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize schema payload", exception);
        }
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid action metadata JSON", exception);
        }
    }

    private Map<String, Object> historyRow(ResultSet resultSet) throws SQLException {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", resultSet.getLong("id"));
        row.put("tableName", resultSet.getString("table_name"));
        row.put("recordId", resultSet.getString("record_id"));
        row.put("operation", resultSet.getString("operation"));
        row.put("oldValue", resultSet.getString("old_value"));
        row.put("newValue", resultSet.getString("new_value"));
        row.put("changedAt", resultSet.getTimestamp("changed_at"));
        return row;
    }

    private Map<String, Object> mapOf(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
