package com.warehouse.ontology.ai;

import com.warehouse.ontology.engine.GenericRepository;
import com.warehouse.ontology.schema.ActionTypeDef;
import com.warehouse.ontology.schema.LinkTypeDef;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.PropertyDef;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds the retrieval context injected into every Claude system prompt.
 * Three layers (mirroring PLTR AIP's Retrieval Context pattern):
 *   1. Schema — full type/link/action definitions (~2K tokens)
 *   2. Summary — aggregate counts + status distributions (~300 tokens)
 *   3. Alerts — pre-computed anomalies (~200 tokens)
 */
@Component
public class RetrievalContextBuilder {

    private final SchemaMetaRepository schemaMetaRepository;
    private final GenericRepository genericRepository;

    public RetrievalContextBuilder(SchemaMetaRepository schemaMetaRepository, GenericRepository genericRepository) {
        this.schemaMetaRepository = schemaMetaRepository;
        this.genericRepository = genericRepository;
    }

    public String build() {
        OntologySchema schema = schemaMetaRepository.getSchema();
        StringBuilder sb = new StringBuilder();

        sb.append("=== ONTOLOGY SCHEMA ===\n\n");
        appendObjectTypes(sb, schema);
        appendLinkTypes(sb, schema);
        appendActionTypes(sb, schema);

        sb.append("\n=== DATA SUMMARY ===\n\n");
        appendDataSummary(sb, schema);

        sb.append("\n=== ACTIVE ALERTS ===\n\n");
        appendAlerts(sb, schema);

        return sb.toString();
    }

    private void appendObjectTypes(StringBuilder sb, OntologySchema schema) {
        sb.append("Object Types:\n");
        for (ObjectTypeDef typeDef : schema.objectTypes().values()) {
            sb.append("  ").append(typeDef.id());
            if (typeDef.description() != null && !typeDef.description().isBlank()) {
                sb.append(" — ").append(typeDef.description());
            }
            sb.append("\n    primaryKey: ").append(typeDef.primaryKey());
            sb.append("\n    properties: ");
            typeDef.properties().forEach((name, prop) -> {
                sb.append(name).append("(").append(prop.type());
                if (prop.required()) sb.append(", required");
                if (prop.uniqueCol()) sb.append(", unique");
                if (!prop.enumValues().isEmpty()) sb.append(", enum=").append(prop.enumValues());
                sb.append("), ");
            });
            sb.append("\n\n");
        }
    }

    private void appendLinkTypes(StringBuilder sb, OntologySchema schema) {
        sb.append("Link Types (relationships):\n");
        for (LinkTypeDef linkDef : schema.linkTypes().values()) {
            sb.append("  ").append(linkDef.id())
              .append(": ").append(linkDef.fromType())
              .append(" → ").append(linkDef.toType())
              .append(" (").append(linkDef.cardinality()).append(")")
              .append(" via foreignKey=").append(linkDef.foreignKey());
            if (linkDef.description() != null && !linkDef.description().isBlank()) {
                sb.append(" — ").append(linkDef.description());
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private void appendActionTypes(StringBuilder sb, OntologySchema schema) {
        if (schema.actionTypes().isEmpty()) {
            sb.append("Action Types: none\n");
            return;
        }
        sb.append("Action Types (executable operations):\n");
        for (ActionTypeDef actionDef : schema.actionTypes().values()) {
            sb.append("  ").append(actionDef.id())
              .append(" on ").append(actionDef.objectTypeId());
            if (actionDef.description() != null && !actionDef.description().isBlank()) {
                sb.append(" — ").append(actionDef.description());
            }
            sb.append("\n    parameters: ").append(actionDef.parametersJson());
            sb.append("\n    preconditions: ").append(actionDef.preconditionsJson());
            sb.append("\n    mutations: ").append(actionDef.mutationsJson());
            sb.append("\n    sideEffects: ").append(actionDef.sideEffectsJson());
            sb.append("\n\n");
        }
    }

    private void appendDataSummary(StringBuilder sb, OntologySchema schema) {
        for (ObjectTypeDef typeDef : schema.objectTypes().values()) {
            try {
                List<Map<String, Object>> objects = genericRepository.findAll(typeDef.id());
                sb.append(typeDef.id()).append(": ").append(objects.size()).append(" records");

                // Show status distribution if the type has a "status" property
                if (typeDef.properties().containsKey("status") && !objects.isEmpty()) {
                    Map<String, Long> distribution = objects.stream()
                            .collect(Collectors.groupingBy(
                                    obj -> String.valueOf(obj.getOrDefault("status", "UNKNOWN")),
                                    LinkedHashMap::new,
                                    Collectors.counting()
                            ));
                    sb.append(" (");
                    distribution.forEach((status, count) ->
                            sb.append(status).append(":").append(count).append(" "));
                    sb.append(")");
                }
                sb.append("\n");
            } catch (Exception e) {
                sb.append(typeDef.id()).append(": error reading\n");
            }
        }
    }

    private void appendAlerts(StringBuilder sb, OntologySchema schema) {
        List<String> alerts = new ArrayList<>();

        // Low battery robots
        if (schema.objectTypes().containsKey("Robot")) {
            try {
                List<Map<String, Object>> robots = genericRepository.findAll("Robot");
                for (Map<String, Object> robot : robots) {
                    Object battery = robot.get("batteryPct");
                    if (battery instanceof Number number && number.intValue() <= 20) {
                        alerts.add("⚠ Robot " + robot.get("id") + " battery=" + number.intValue()
                                + "% (status: " + robot.get("status") + ")");
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // Failed tasks
        if (schema.objectTypes().containsKey("Task")) {
            try {
                List<Map<String, Object>> failedTasks = genericRepository.findByProperty("Task", "status", "FAILED");
                for (Map<String, Object> task : failedTasks) {
                    alerts.add("⚠ Task " + task.get("id") + " FAILED (type: " + task.get("type")
                            + ", robot: " + task.get("robotId") + ")");
                }
            } catch (Exception ignored) {
            }
        }

        if (alerts.isEmpty()) {
            sb.append("No active alerts.\n");
        } else {
            alerts.forEach(alert -> sb.append(alert).append("\n"));
        }
    }
}
