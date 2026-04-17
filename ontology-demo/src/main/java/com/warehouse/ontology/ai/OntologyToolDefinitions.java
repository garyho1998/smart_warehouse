package com.warehouse.ontology.ai;

import java.util.List;
import java.util.Map;

/**
 * Defines the 5 ontology-native tools exposed to Claude via the Messages API.
 * Returns plain Maps that serialize directly to the Claude API tool schema format.
 */
public final class OntologyToolDefinitions {

    private OntologyToolDefinitions() {
    }

    public static List<Map<String, Object>> allTools() {
        return List.of(searchObjects(), getObject(), exploreConnections(), analyzeAnomaly(), executeAction());
    }

    static Map<String, Object> searchObjects() {
        return Map.of(
                "name", "search_objects",
                "description", "Search for objects of a given ontology type. "
                        + "Optionally filter by a single property value. "
                        + "Returns a list of matching objects with all properties.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The ontology object type to search (e.g. Robot, Task, Warehouse)"),
                                "filterProperty", Map.of("type", "string",
                                        "description", "Optional property name to filter on (e.g. status, type)"),
                                "filterValue", Map.of("type", "string",
                                        "description", "Value to match for the filter property")
                        ),
                        "required", List.of("objectType")
                )
        );
    }

    static Map<String, Object> getObject() {
        return Map.of(
                "name", "get_object",
                "description", "Get a single object by type and ID. Returns all properties of the object.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "objectType", Map.of("type", "string",
                                        "description", "The ontology object type"),
                                "objectId", Map.of("type", "string",
                                        "description", "The ID of the object")
                        ),
                        "required", List.of("objectType", "objectId")
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

    static Map<String, Object> executeAction() {
        return Map.of(
                "name", "execute_action",
                "description", "Execute an ontology action (write operation). "
                        + "Actions have preconditions that must be satisfied. "
                        + "IMPORTANT: Only call this AFTER the user has confirmed they want to proceed. "
                        + "Always describe what will happen and ask for confirmation first.",
                "input_schema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "actionName", Map.of("type", "string",
                                        "description", "The action type name (e.g. completeTask)"),
                                "parameters", Map.of("type", "object",
                                        "description", "Action parameters including the object ID key "
                                                + "(e.g. {\"taskId\": \"TSK-001\", \"actor\": \"AI Assistant\"})")
                        ),
                        "required", List.of("actionName", "parameters")
                )
        );
    }
}
