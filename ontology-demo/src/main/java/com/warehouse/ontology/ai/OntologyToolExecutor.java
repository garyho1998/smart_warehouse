package com.warehouse.ontology.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.warehouse.ontology.engine.ActionExecutor;
import com.warehouse.ontology.engine.ActionResult;
import com.warehouse.ontology.engine.GenericRepository;
import com.warehouse.ontology.engine.GraphResult;
import com.warehouse.ontology.engine.GraphService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dispatches Claude tool calls to the corresponding Java services.
 * Returns a ToolExecutionResult containing both the text result for Claude
 * and optional structured data for frontend rendering (graph, table).
 */
@Component
public class OntologyToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(OntologyToolExecutor.class);

    private final GenericRepository genericRepository;
    private final GraphService graphService;
    private final ActionExecutor actionExecutor;
    private final ObjectMapper objectMapper;

    public OntologyToolExecutor(
            GenericRepository genericRepository,
            GraphService graphService,
            ActionExecutor actionExecutor,
            ObjectMapper objectMapper
    ) {
        this.genericRepository = genericRepository;
        this.graphService = graphService;
        this.actionExecutor = actionExecutor;
        this.objectMapper = objectMapper;
    }

    public ToolExecutionResult execute(String toolName, JsonNode input) {
        try {
            return switch (toolName) {
                case "search_objects" -> executeSearchObjects(input);
                case "get_object" -> executeGetObject(input);
                case "explore_connections" -> executeExploreConnections(input);
                case "analyze_anomaly" -> executeAnalyzeAnomaly(input);
                case "execute_action" -> executeExecuteAction(input);
                default -> ToolExecutionResult.textOnly("Unknown tool: " + toolName);
            };
        } catch (Exception e) {
            log.warn("Tool execution failed: {} — {}", toolName, e.getMessage());
            return ToolExecutionResult.textOnly("Error executing " + toolName + ": " + e.getMessage());
        }
    }

    private ToolExecutionResult executeSearchObjects(JsonNode input) throws JsonProcessingException {
        String objectType = input.get("objectType").asText();
        JsonNode filterProp = input.get("filterProperty");
        JsonNode filterVal = input.get("filterValue");

        List<Map<String, Object>> results;
        if (filterProp != null && !filterProp.isNull() && filterVal != null && !filterVal.isNull()) {
            results = genericRepository.findByProperty(objectType, filterProp.asText(), filterVal.asText());
        } else {
            results = genericRepository.findAll(objectType);
        }

        String json = objectMapper.writeValueAsString(results);
        return new ToolExecutionResult(
                results.size() + " " + objectType + " objects found:\n" + json,
                "table",
                Map.of("type", objectType, "objects", results)
        );
    }

    private ToolExecutionResult executeGetObject(JsonNode input) throws JsonProcessingException {
        String objectType = input.get("objectType").asText();
        String objectId = input.get("objectId").asText();

        Optional<Map<String, Object>> result = genericRepository.findById(objectType, objectId);
        if (result.isEmpty()) {
            return ToolExecutionResult.textOnly("Object not found: " + objectType + ":" + objectId);
        }

        String json = objectMapper.writeValueAsString(result.get());
        return new ToolExecutionResult(json, "object", Map.of("type", objectType, "object", result.get()));
    }

    private ToolExecutionResult executeExploreConnections(JsonNode input) throws JsonProcessingException {
        String objectType = input.get("objectType").asText();
        String objectId = input.get("objectId").asText();
        int depth = input.has("depth") ? input.get("depth").asInt(3) : 3;

        GraphResult graphResult = graphService.traverse(objectType, objectId, depth);
        String json = objectMapper.writeValueAsString(graphResult);
        return new ToolExecutionResult(
                "Graph traversal from " + objectType + ":" + objectId + " (depth=" + depth + "):\n"
                        + graphResult.nodes().size() + " nodes, " + graphResult.edges().size() + " edges\n" + json,
                "graph",
                Map.of("graphResult", graphResult)
        );
    }

    private ToolExecutionResult executeAnalyzeAnomaly(JsonNode input) throws JsonProcessingException {
        String objectType = input.has("objectType") ? input.get("objectType").asText() : "Task";
        String objectId = input.has("objectId") ? input.get("objectId").asText() : input.get("taskId").asText();
        GraphResult graphResult = graphService.traceAnomaly(objectType, objectId);

        String insightsSummary = graphResult.insights().isEmpty()
                ? "No anomalies detected."
                : graphResult.insights().stream()
                        .map(i -> "[" + i.severity() + "] " + i.message())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");

        String json = objectMapper.writeValueAsString(graphResult);
        return new ToolExecutionResult(
                "Anomaly analysis for " + objectType + " " + objectId + ":\n"
                        + insightsSummary + "\n"
                        + graphResult.nodes().size() + " nodes, " + graphResult.edges().size() + " edges\n" + json,
                "graph",
                Map.of("graphResult", graphResult, "insights", graphResult.insights())
        );
    }

    @SuppressWarnings("unchecked")
    private ToolExecutionResult executeExecuteAction(JsonNode input) throws JsonProcessingException {
        String actionName = input.get("actionName").asText();
        JsonNode paramsNode = input.get("parameters");
        Map<String, Object> parameters = paramsNode != null
                ? objectMapper.convertValue(paramsNode, LinkedHashMap.class)
                : Map.of();

        ActionResult result = actionExecutor.execute(actionName, parameters);
        String json = objectMapper.writeValueAsString(result);
        return new ToolExecutionResult(
                "Action " + actionName + " executed successfully on " + result.objectType() + ":" + result.objectId()
                        + "\nUpdated state: " + json,
                "action_result",
                Map.of("actionResult", result)
        );
    }

    public record ToolExecutionResult(
            String textForClaude,
            String displayType,
            Map<String, Object> displayData
    ) {
        static ToolExecutionResult textOnly(String text) {
            return new ToolExecutionResult(text, "text", Map.of());
        }
    }
}
