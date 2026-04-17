package com.warehouse.ontology.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SchemaBootstrap {

    private final SchemaMetaRepository schemaMetaRepository;
    private final ObjectMapper objectMapper;
    private final Resource ontologyResource = new ClassPathResource("ontology.yml");

    public SchemaBootstrap(SchemaMetaRepository schemaMetaRepository, ObjectMapper objectMapper) {
        this.schemaMetaRepository = schemaMetaRepository;
        this.objectMapper = objectMapper;
    }

    public void seedIfEmpty() {
        Map<String, Object> root = loadOntologyDefinition();

        if (schemaMetaRepository.countObjectTypes() == 0) {
            seedObjectTypes(asMap(root.get("objectTypes")));
            seedLinkTypes(asMap(root.get("linkTypes")));
            seedActionTypes(asMap(root.get("actionTypes")));
        }

        // Rules/functions tables may be new — seed independently
        if (schemaMetaRepository.countRules() == 0) {
            seedRules(asMap(root.get("rules")));
            seedFunctions(asMap(root.get("functions")));
        }

        schemaMetaRepository.invalidateCache();
    }

    private void seedObjectTypes(Map<String, Object> objectTypes) {
        objectTypes.forEach((typeName, rawTypeDef) -> {
            Map<String, Object> typeDefMap = asMap(rawTypeDef);
            ObjectTypeDef objectTypeDef = new ObjectTypeDef(
                    typeName,
                    stringValue(typeDefMap.get("description")),
                    stringValue(typeDefMap.getOrDefault("primaryKey", "id")),
                    Map.of()
            );
            schemaMetaRepository.insertObjectType(objectTypeDef);
            schemaMetaRepository.recordSchemaChange(
                    "object_type_def",
                    typeName,
                    "INSERT",
                    null,
                    toJson(mapOf(
                            "id", objectTypeDef.id(),
                            "description", objectTypeDef.description(),
                            "primaryKey", objectTypeDef.primaryKey()
                    ))
            );

            Map<String, Object> properties = asMap(typeDefMap.get("properties"));
            properties.forEach((propertyName, rawPropertyDef) -> {
                PropertyDef propertyDef = propertyDef(typeName, propertyName, rawPropertyDef);
                schemaMetaRepository.insertProperty(propertyDef);
                schemaMetaRepository.recordSchemaChange(
                        "property_def",
                        typeName + ":" + propertyName,
                        "INSERT",
                        null,
                        toJson(mapOf(
                                "objectTypeId", propertyDef.objectTypeId(),
                                "name", propertyDef.name(),
                                "type", propertyDef.type(),
                                "required", propertyDef.required(),
                                "uniqueCol", propertyDef.uniqueCol(),
                                "defaultValue", propertyDef.defaultValue(),
                                "enumValues", propertyDef.enumValues()
                        ))
                );
            });
        });
    }

    private void seedLinkTypes(Map<String, Object> linkTypes) {
        linkTypes.forEach((linkName, rawLinkDef) -> {
            Map<String, Object> linkDefMap = asMap(rawLinkDef);
            LinkTypeDef linkTypeDef = new LinkTypeDef(
                    linkName,
                    stringValue(linkDefMap.get("from")),
                    stringValue(linkDefMap.get("to")),
                    stringValue(linkDefMap.get("foreignKey")),
                    stringValue(linkDefMap.get("cardinality")),
                    stringValue(linkDefMap.get("description"))
            );
            schemaMetaRepository.insertLinkType(linkTypeDef);
            schemaMetaRepository.recordSchemaChange(
                    "link_type_def",
                    linkTypeDef.id(),
                    "INSERT",
                    null,
                    toJson(mapOf(
                            "id", linkTypeDef.id(),
                            "fromType", linkTypeDef.fromType(),
                            "toType", linkTypeDef.toType(),
                            "foreignKey", linkTypeDef.foreignKey(),
                            "cardinality", linkTypeDef.cardinality(),
                            "description", linkTypeDef.description()
                    ))
            );
        });
    }

    private void seedActionTypes(Map<String, Object> actionTypes) {
        actionTypes.forEach((actionName, rawActionDef) -> {
            Map<String, Object> actionDefMap = asMap(rawActionDef);
            ActionTypeDef actionTypeDef = new ActionTypeDef(
                    actionName,
                    stringValue(actionDefMap.get("description")),
                    stringValue(actionDefMap.get("objectType")),
                    toJson(actionDefMap.get("parameters")),
                    toJson(actionDefMap.get("preconditions")),
                    toJson(actionDefMap.get("mutations")),
                    toJson(actionDefMap.get("sideEffects")),
                    booleanValue(actionDefMap.getOrDefault("audit", Boolean.TRUE)),
                    stringValue(actionDefMap.get("mode"))
            );
            schemaMetaRepository.insertActionType(actionTypeDef);
            schemaMetaRepository.recordSchemaChange(
                    "action_type_def",
                    actionTypeDef.id(),
                    "INSERT",
                    null,
                    toJson(mapOf(
                            "id", actionTypeDef.id(),
                            "description", actionTypeDef.description(),
                            "objectTypeId", actionTypeDef.objectTypeId(),
                            "parameters", actionDefMap.get("parameters"),
                            "preconditions", actionDefMap.get("preconditions"),
                            "mutations", actionDefMap.get("mutations"),
                            "sideEffects", actionDefMap.get("sideEffects"),
                            "audit", actionTypeDef.audit()
                    ))
            );
        });
    }

    private void seedRules(Map<String, Object> rulesMap) {
        rulesMap.forEach((ruleName, rawRule) -> {
            Map<String, Object> ruleMap = asMap(rawRule);
            RuleDef.Condition when = parseCondition(asMap(ruleMap.get("when")));
            RuleDef.Check check = null;
            if (ruleMap.containsKey("check")) {
                Map<String, Object> checkMap = asMap(ruleMap.get("check"));
                check = new RuleDef.Check(
                        stringValue(checkMap.get("follow")),
                        parseCondition(asMap(checkMap.get("condition")))
                );
            }
            RuleDef ruleDef = new RuleDef(
                    ruleName,
                    stringValue(ruleMap.get("description")),
                    stringValue(ruleMap.get("objectType")),
                    stringValue(ruleMap.get("severity")),
                    when,
                    check,
                    stringValue(ruleMap.get("message"))
            );
            schemaMetaRepository.insertRule(ruleDef);
            schemaMetaRepository.recordSchemaChange("rule_def", ruleName, "INSERT", null,
                    toJson(mapOf("id", ruleName, "objectType", ruleDef.objectType(),
                            "severity", ruleDef.severity())));
        });
    }

    private RuleDef.Condition parseCondition(Map<String, Object> condMap) {
        if (condMap.isEmpty()) {
            return null;
        }
        return new RuleDef.Condition(
                stringValue(condMap.get("property")),
                stringValue(condMap.get("operator")),
                condMap.get("value")
        );
    }

    private void seedFunctions(Map<String, Object> functionsMap) {
        functionsMap.forEach((funcName, rawFunc) -> {
            Map<String, Object> funcMap = asMap(rawFunc);
            List<FunctionDef.Case> cases = new ArrayList<>();
            Object rawCases = funcMap.get("cases");
            if (rawCases instanceof List<?> caseList) {
                for (Object rawCase : caseList) {
                    Map<String, Object> caseMap = asMap(rawCase);
                    FunctionDef.Condition when = null;
                    if (caseMap.containsKey("when")) {
                        Map<String, Object> whenMap = asMap(caseMap.get("when"));
                        when = new FunctionDef.Condition(
                                stringValue(whenMap.get("property")),
                                stringValue(whenMap.get("operator")),
                                whenMap.get("value")
                        );
                    }
                    cases.add(new FunctionDef.Case(when, stringValue(caseMap.get("then"))));
                }
            }
            if (funcMap.containsKey("default")) {
                cases.add(new FunctionDef.Case(null, stringValue(funcMap.get("default"))));
            }
            FunctionDef functionDef = new FunctionDef(funcName,
                    stringValue(funcMap.get("objectType")),
                    stringValue(funcMap.get("returnType")),
                    cases);
            schemaMetaRepository.insertFunction(functionDef);
            schemaMetaRepository.recordSchemaChange("function_def", funcName, "INSERT", null,
                    toJson(mapOf("id", funcName, "objectType", functionDef.objectType(),
                            "returnType", functionDef.returnType())));
        });
    }

    private PropertyDef propertyDef(String objectTypeId, String propertyName, Object rawPropertyDef) {
        Map<String, Object> propertyDefMap = asMap(rawPropertyDef);
        return new PropertyDef(
                null,
                objectTypeId,
                propertyName,
                stringValue(propertyDefMap.get("type")),
                booleanValue(propertyDefMap.get("required")),
                booleanValue(propertyDefMap.get("unique")),
                propertyDefMap.containsKey("default") ? stringValue(propertyDefMap.get("default")) : null,
                stringList(propertyDefMap.get("values"))
        );
    }

    private Map<String, Object> loadOntologyDefinition() {
        try (InputStream inputStream = ontologyResource.getInputStream()) {
            Object loaded = new Yaml().load(inputStream);
            return asMap(loaded);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read ontology seed YAML", exception);
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, rawValue) -> map.put(String.valueOf(key), rawValue));
        return map;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream().map(String::valueOf).toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize schema bootstrap payload", exception);
        }
    }

    private Map<String, Object> mapOf(Object... values) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
