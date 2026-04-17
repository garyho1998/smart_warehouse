package com.warehouse.ontology.ai;

import com.warehouse.ontology.schema.ActionTypeDef;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

/**
 * Schema-driven tool definitions for Claude.
 * PLTR pattern: tools are generated from the Ontology, not hardcoded.
 * When schema changes (new Action Types, Object Types), tools auto-update.
 */
@Component
public class OntologyToolDefinitions {

    private final SchemaMetaRepository schemaMetaRepository;

    public OntologyToolDefinitions(SchemaMetaRepository schemaMetaRepository) {
        this.schemaMetaRepository = schemaMetaRepository;
    }

    public List<Map<String, Object>> allTools() {
        OntologySchema schema = schemaMetaRepository.getSchema();
        List<Map<String, Object>> tools = new ArrayList<>();
        tools.add(searchObjects(schema));
        tools.add(getObject(schema));
        tools.add(createObject(schema));
        tools.add(updateObject(schema));
        tools.add(exploreConnections());
        tools.add(analyzeAnomaly());
        tools.add(executeAction(schema));
        return List.copyOf(tools);
    }

    private static Map<String, Object> searchObjects(OntologySchema schema) {
        String typeList = String.join(", ", schema.objectTypes().keySet());
        return Map.of(
                "name", "search_objects",
                "description", "Search for objects of a given ontology type. "
                        + "Optionally filter by a single property value. "
                        + "Returns a list of matching objects with all properties. "
                        + "Available types: " + typeList,
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The ontology object type. Must be one of: " + typeList),
                                "filterProperty", Map.of("type", "string",
                                        "description", "Optional property name to filter on (e.g. status, type)"),
                                "filterValue", Map.of("type", "string",
                                        "description", "Value to match for the filter property")
                        ),
                        "required", List.of("objectType")
                )
        );
    }

    private static Map<String, Object> getObject(OntologySchema schema) {
        String typeList = String.join(", ", schema.objectTypes().keySet());
        return Map.of(
                "name", "get_object",
                "description", "Get a single object by type and ID. Returns all properties of the object. "
                        + "Available types: " + typeList,
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The ontology object type. Must be one of: " + typeList),
                                "objectId", Map.of("type", "string",
                                        "description", "The ID of the object")
                        ),
                        "required", List.of("objectType", "objectId")
                )
        );
    }

    private static Map<String, Object> createObject(OntologySchema schema) {
        StringJoiner typeDetails = new StringJoiner("; ");
        for (ObjectTypeDef typeDef : schema.objectTypes().values()) {
            StringJoiner props = new StringJoiner(", ");
            typeDef.properties().values().forEach(p -> {
                String desc = p.name() + "(" + p.type();
                if (p.required()) desc += ", required";
                if (!p.enumValues().isEmpty()) desc += ", enum=" + p.enumValues();
                desc += ")";
                props.add(desc);
            });
            typeDetails.add(typeDef.id() + " [" + props + "]");
        }
        return Map.of(
                "name", "create_object",
                "description", "Create a new object of a given ontology type. "
                        + "Provide all required properties as key-value pairs. "
                        + "The 'id' field must be included. "
                        + "Returns the created object. "
                        + "Available types and their properties: " + typeDetails,
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The ontology object type to create"),
                                "properties", Map.of("type", "object",
                                        "description", "Key-value pairs for the new object's properties including id")
                        ),
                        "required", List.of("objectType", "properties")
                )
        );
    }

    private static Map<String, Object> updateObject(OntologySchema schema) {
        String typeList = String.join(", ", schema.objectTypes().keySet());
        return Map.of(
                "name", "update_object",
                "description", "Update an existing object's properties. "
                        + "Only the specified properties will be changed; others remain untouched. "
                        + "Cannot update the primary key. "
                        + "Available types: " + typeList,
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The ontology object type. Must be one of: " + typeList),
                                "objectId", Map.of("type", "string",
                                        "description", "The ID of the object to update"),
                                "properties", Map.of("type", "object",
                                        "description", "Key-value pairs of properties to update")
                        ),
                        "required", List.of("objectType", "objectId", "properties")
                )
        );
    }

    static Map<String, Object> exploreConnections() {
        return Map.of(
                "name", "explore_connections",
                "description", "Traverse the ontology graph starting from a given object. "
                        + "Follows link types in both directions to discover connected objects. "
                        + "Returns nodes, edges, and any insights found during traversal. "
                        + "The result can be displayed as a graph visualization.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The starting object's type"),
                                "objectId", Map.of("type", "string",
                                        "description", "The starting object's ID"),
                                "depth", Map.of("type", "integer",
                                        "description", "How many hops to traverse (1-4, default 3)")
                        ),
                        "required", List.of("objectType", "objectId")
                )
        );
    }

    static Map<String, Object> analyzeAnomaly() {
        return Map.of(
                "name", "analyze_anomaly",
                "description", "Analyze a Task for anomalies and root causes. "
                        + "Traces the task's connections and checks for issues like "
                        + "low robot battery, failed status impacts on orders, etc. "
                        + "Returns graph data plus insight messages with severity levels.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "taskId", Map.of("type", "string",
                                        "description", "The Task ID to analyze")
                        ),
                        "required", List.of("taskId")
                )
        );
    }

    private static Map<String, Object> executeAction(OntologySchema schema) {
        StringJoiner actionDescriptions = new StringJoiner("\n");
        if (schema.actionTypes().isEmpty()) {
            actionDescriptions.add("No actions currently defined.");
        } else {
            for (ActionTypeDef actionDef : schema.actionTypes().values()) {
                actionDescriptions.add("- " + actionDef.id()
                        + " (on " + actionDef.objectTypeId() + ")"
                        + ": " + (actionDef.description() != null ? actionDef.description() : "")
                        + " | params: " + actionDef.parametersJson()
                        + " | preconditions: " + actionDef.preconditionsJson()
                        + " | mutations: " + actionDef.mutationsJson()
                        + " | sideEffects: " + actionDef.sideEffectsJson());
            }
        }

        String actionNames = schema.actionTypes().isEmpty()
                ? "none"
                : String.join(", ", schema.actionTypes().keySet());

        return Map.of(
                "name", "execute_action",
                "description", "Execute an ontology action (write operation with preconditions, mutations, and side effects). "
                        + "IMPORTANT: Only call AFTER the user has confirmed. "
                        + "Available actions: " + actionNames + "\n"
                        + "Action details:\n" + actionDescriptions
                        + "\nDo NOT invent action names — only the above actions exist. "
                        + "To create new objects, use create_object instead.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "actionName", Map.of("type", "string",
                                        "description", "Must be one of: " + actionNames),
                                "parameters", Map.of("type", "object",
                                        "description", "Action parameters including the object ID key")
                        ),
                        "required", List.of("actionName", "parameters")
                )
        );
    }
}
