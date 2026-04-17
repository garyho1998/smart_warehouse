package com.warehouse.ontology.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaMetaRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private volatile OntologySchema cached;

    public SchemaMetaRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public OntologySchema getSchema() {
        OntologySchema localCache = cached;
        if (localCache != null) {
            return localCache;
        }

        synchronized (this) {
            if (cached == null) {
                cached = loadSchema();
            }
            return cached;
        }
    }

    public void invalidateCache() {
        cached = null;
    }

    public long countObjectTypes() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM object_type_def", Long.class);
        return count == null ? 0L : count;
    }

    public long countRules() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rule_def", Long.class);
        return count == null ? 0L : count;
    }

    // ── Insert methods ────────────────────────────────────────────────

    public void insertObjectType(ObjectTypeDef def) {
        jdbcTemplate.update(
                "INSERT INTO object_type_def (id, description, primary_key) VALUES (?, ?, ?)",
                def.id(),
                def.description(),
                def.primaryKey()
        );
    }

    public void insertProperty(PropertyDef def) {
        jdbcTemplate.update(
                "INSERT INTO property_def (object_type_id, name, type, required, unique_col, default_value, enum_values) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                def.objectTypeId(),
                def.name(),
                def.type(),
                def.required(),
                def.uniqueCol(),
                def.defaultValue(),
                commaSeparated(def.enumValues())
        );
    }

    public void insertLinkType(LinkTypeDef def) {
        jdbcTemplate.update(
                "INSERT INTO link_type_def (id, from_type, to_type, foreign_key, cardinality, description) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                def.id(),
                def.fromType(),
                def.toType(),
                def.foreignKey(),
                def.cardinality(),
                def.description()
        );
    }

    public void insertActionType(ActionTypeDef def) {
        jdbcTemplate.update(
                "INSERT INTO action_type_def (id, description, object_type_id, parameters, preconditions, mutations, side_effects, audit) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                def.id(),
                def.description(),
                def.objectTypeId(),
                def.parametersJson(),
                def.preconditionsJson(),
                def.mutationsJson(),
                def.sideEffectsJson(),
                def.audit()
        );
    }

    public void insertRule(RuleDef def) {
        jdbcTemplate.update(
                "INSERT INTO rule_def (id, description, object_type_id, severity, when_json, check_json, message) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                def.id(),
                def.description(),
                def.objectType(),
                def.severity(),
                toJson(def.when()),
                toJson(def.check()),
                def.message()
        );
    }

    public void insertFunction(FunctionDef def) {
        jdbcTemplate.update(
                "INSERT INTO function_def (id, object_type_id, return_type, cases_json) "
                        + "VALUES (?, ?, ?, ?)",
                def.id(),
                def.objectType(),
                def.returnType(),
                toJson(def.cases())
        );
    }

    public void recordSchemaChange(String tableName, String recordId, String operation, String oldValue, String newValue) {
        jdbcTemplate.update(
                "INSERT INTO schema_history (table_name, record_id, operation, old_value, new_value) VALUES (?, ?, ?, ?, ?)",
                tableName,
                recordId,
                operation,
                oldValue,
                newValue
        );
    }

    // ── Load schema from DB ───────────────────────────────────────────

    private OntologySchema loadSchema() {
        Map<String, LinkedHashMap<String, PropertyDef>> propertiesByType = loadPropertiesByType();

        Map<String, ObjectTypeDef> objectTypes = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, description, primary_key FROM object_type_def ORDER BY id",
                rs -> {
                    String id = rs.getString("id");
                    objectTypes.put(id, new ObjectTypeDef(
                            id,
                            rs.getString("description"),
                            rs.getString("primary_key"),
                            propertiesByType.getOrDefault(id, new LinkedHashMap<>())
                    ));
                }
        );

        Map<String, LinkTypeDef> linkTypes = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, from_type, to_type, foreign_key, cardinality, description FROM link_type_def ORDER BY id",
                rs -> {
                    linkTypes.put(rs.getString("id"), new LinkTypeDef(
                            rs.getString("id"),
                            rs.getString("from_type"),
                            rs.getString("to_type"),
                            rs.getString("foreign_key"),
                            rs.getString("cardinality"),
                            rs.getString("description")
                    ));
                }
        );

        Map<String, ActionTypeDef> actionTypes = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, description, object_type_id, parameters, preconditions, mutations, side_effects, audit "
                        + "FROM action_type_def ORDER BY id",
                rs -> {
                    actionTypes.put(rs.getString("id"), new ActionTypeDef(
                            rs.getString("id"),
                            rs.getString("description"),
                            rs.getString("object_type_id"),
                            rs.getString("parameters"),
                            rs.getString("preconditions"),
                            rs.getString("mutations"),
                            rs.getString("side_effects"),
                            rs.getBoolean("audit")
                    ));
                }
        );

        Map<String, RuleDef> rules = loadRules();
        Map<String, FunctionDef> functions = loadFunctions();

        return new OntologySchema(objectTypes, linkTypes, actionTypes, rules, functions);
    }

    private Map<String, RuleDef> loadRules() {
        Map<String, RuleDef> rules = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, description, object_type_id, severity, when_json, check_json, message "
                        + "FROM rule_def ORDER BY id",
                rs -> {
                    RuleDef.Condition when = fromJson(rs.getString("when_json"), RuleDef.Condition.class);
                    RuleDef.Check check = fromJson(rs.getString("check_json"), RuleDef.Check.class);
                    rules.put(rs.getString("id"), new RuleDef(
                            rs.getString("id"),
                            rs.getString("description"),
                            rs.getString("object_type_id"),
                            rs.getString("severity"),
                            when,
                            check,
                            rs.getString("message")
                    ));
                }
        );
        return rules;
    }

    private Map<String, FunctionDef> loadFunctions() {
        Map<String, FunctionDef> functions = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT id, object_type_id, return_type, cases_json FROM function_def ORDER BY id",
                rs -> {
                    List<FunctionDef.Case> cases = fromJsonList(
                            rs.getString("cases_json"),
                            new TypeReference<List<FunctionDef.Case>>() {}
                    );
                    functions.put(rs.getString("id"), new FunctionDef(
                            rs.getString("id"),
                            rs.getString("object_type_id"),
                            rs.getString("return_type"),
                            cases != null ? cases : List.of()
                    ));
                }
        );
        return functions;
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Map<String, LinkedHashMap<String, PropertyDef>> loadPropertiesByType() {
        Map<String, LinkedHashMap<String, PropertyDef>> propertiesByType = new LinkedHashMap<>();

        jdbcTemplate.query(
                "SELECT id, object_type_id, name, type, required, unique_col, default_value, enum_values "
                        + "FROM property_def ORDER BY object_type_id, id",
                rs -> {
                    PropertyDef propertyDef = new PropertyDef(
                            rs.getLong("id"),
                            rs.getString("object_type_id"),
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getBoolean("required"),
                            rs.getBoolean("unique_col"),
                            rs.getString("default_value"),
                            splitValues(rs.getString("enum_values"))
                    );
                    propertiesByType
                            .computeIfAbsent(propertyDef.objectTypeId(), ignored -> new LinkedHashMap<>())
                            .put(propertyDef.name(), propertyDef);
                }
        );

        return propertiesByType;
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize to JSON", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize JSON: " + json, e);
        }
    }

    private <T> T fromJsonList(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize JSON list: " + json, e);
        }
    }

    private List<String> splitValues(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(",")).stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
    }

    private String commaSeparated(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join(",", values);
    }
}
