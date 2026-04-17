package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.LinkTypeDef;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.OntologySchema;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import com.warehouse.ontology.support.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class GraphService {

    private static final int MAX_DEPTH_CAP = 4;

    private final GenericRepository genericRepository;
    private final SchemaMetaRepository schemaMetaRepository;
    private final RuleEngine ruleEngine;
    private final FunctionEngine functionEngine;

    public GraphService(
            GenericRepository genericRepository,
            SchemaMetaRepository schemaMetaRepository,
            RuleEngine ruleEngine,
            FunctionEngine functionEngine) {
        this.genericRepository = genericRepository;
        this.schemaMetaRepository = schemaMetaRepository;
        this.ruleEngine = ruleEngine;
        this.functionEngine = functionEngine;
    }

    public GraphResult traverse(String startType, String startId, int maxDepth) {
        OntologySchema schema = schemaMetaRepository.getSchema();
        Map<String, Object> startObject = genericRepository.findById(startType, startId)
                .orElseThrow(() -> new NotFoundException(
                        "Unknown object instance: " + startType + ":" + startId
                ));

        TraversalState traversalState = new TraversalState();
        traverseRecursive(
                schema,
                startType,
                startObject,
                clampDepth(maxDepth),
                traversalState
        );

        GraphNode startNode = traversalState.nodes().get(nodeKey(startType, startId));
        return new GraphResult(
                startNode,
                new ArrayList<>(traversalState.nodes().values()),
                traversalState.edges(),
                List.of()
        );
    }

    /**
     * Generic anomaly tracing — works for ANY object type.
     * Rules are evaluated from ontology.yml, not hardcoded.
     */
    public GraphResult traceAnomaly(String objectType, String objectId) {
        OntologySchema schema = schemaMetaRepository.getSchema();
        GraphResult graphResult = traverse(objectType, objectId, 3);
        Map<String, Object> object = genericRepository.findById(objectType, objectId)
                .orElseThrow(() -> new NotFoundException(
                        "Unknown object instance: " + objectType + ":" + objectId));

        List<GraphInsight> insights = ruleEngine.evaluate(objectType, object, schema);
        return new GraphResult(
                graphResult.start(),
                graphResult.nodes(),
                graphResult.edges(),
                insights
        );
    }

    private void traverseRecursive(
            OntologySchema schema,
            String currentType,
            Map<String, Object> currentObject,
            int remainingDepth,
            TraversalState traversalState
    ) {
        ObjectTypeDef currentTypeDef = schema.requireObjectType(currentType);
        String currentId = String.valueOf(currentObject.get(currentTypeDef.primaryKey()));
        String currentKey = nodeKey(currentType, currentId);
        traversalState.nodes().putIfAbsent(currentKey, graphNode(currentType, currentId, currentObject));

        if (!traversalState.visited().add(currentKey) || remainingDepth == 0) {
            return;
        }

        for (LinkTypeDef linkTypeDef : schema.linkTypes().values()) {
            for (RelatedObject relatedObject : resolveRelated(schema, currentType, currentObject, linkTypeDef)) {
                String relatedKey = nodeKey(relatedObject.type(), relatedObject.id());
                traversalState.nodes().putIfAbsent(
                        relatedKey,
                        graphNode(relatedObject.type(), relatedObject.id(), relatedObject.payload())
                );
                // Use canonical direction: fromType → toType (not traversal direction)
                boolean currentIsFrom = currentType.equals(linkTypeDef.fromType());
                String edgeFrom = currentIsFrom ? currentKey : relatedKey;
                String edgeTo = currentIsFrom ? relatedKey : currentKey;
                addEdge(linkTypeDef.id(), edgeFrom, edgeTo, traversalState);
                if (!traversalState.visited().contains(relatedKey)) {
                    traverseRecursive(
                            schema,
                            relatedObject.type(),
                            relatedObject.payload(),
                            remainingDepth - 1,
                            traversalState
                    );
                }
            }
        }
    }

    private List<RelatedObject> resolveRelated(
            OntologySchema schema,
            String currentType,
            Map<String, Object> currentObject,
            LinkTypeDef linkTypeDef
    ) {
        if (currentType.equals(linkTypeDef.fromType())) {
            return resolveFromSide(schema, currentObject, linkTypeDef);
        }
        if (currentType.equals(linkTypeDef.toType())) {
            return resolveToSide(schema, currentObject, linkTypeDef);
        }
        return List.of();
    }

    private List<RelatedObject> resolveFromSide(
            OntologySchema schema,
            Map<String, Object> currentObject,
            LinkTypeDef linkTypeDef
    ) {
        ObjectTypeDef fromTypeDef = schema.requireObjectType(linkTypeDef.fromType());
        ObjectTypeDef toTypeDef = schema.requireObjectType(linkTypeDef.toType());
        String currentId = String.valueOf(currentObject.get(fromTypeDef.primaryKey()));

        if (fromTypeDef.properties().containsKey(linkTypeDef.foreignKey())) {
            Object foreignKeyValue = currentObject.get(linkTypeDef.foreignKey());
            return fetchSingle(linkTypeDef.toType(), foreignKeyValue);
        }
        if (toTypeDef.properties().containsKey(linkTypeDef.foreignKey())) {
            return fetchMany(linkTypeDef.toType(), linkTypeDef.foreignKey(), currentId);
        }
        return List.of();
    }

    private List<RelatedObject> resolveToSide(
            OntologySchema schema,
            Map<String, Object> currentObject,
            LinkTypeDef linkTypeDef
    ) {
        ObjectTypeDef fromTypeDef = schema.requireObjectType(linkTypeDef.fromType());
        ObjectTypeDef toTypeDef = schema.requireObjectType(linkTypeDef.toType());
        String currentId = String.valueOf(currentObject.get(toTypeDef.primaryKey()));

        if (toTypeDef.properties().containsKey(linkTypeDef.foreignKey())) {
            Object foreignKeyValue = currentObject.get(linkTypeDef.foreignKey());
            return fetchSingle(linkTypeDef.fromType(), foreignKeyValue);
        }
        if (fromTypeDef.properties().containsKey(linkTypeDef.foreignKey())) {
            return fetchMany(linkTypeDef.fromType(), linkTypeDef.foreignKey(), currentId);
        }
        return List.of();
    }

    private List<RelatedObject> fetchSingle(String typeName, Object idValue) {
        if (idValue == null || String.valueOf(idValue).isBlank()) {
            return List.of();
        }

        Optional<Map<String, Object>> relatedObject = genericRepository.findById(typeName, String.valueOf(idValue));
        return relatedObject
                .map(object -> List.of(new RelatedObject(
                        typeName,
                        relatedId(typeName, object),
                        object
                )))
                .orElseGet(List::of);
    }

    private List<RelatedObject> fetchMany(String typeName, String propertyName, String value) {
        return genericRepository.findByProperty(typeName, propertyName, value).stream()
                .map(object -> new RelatedObject(typeName, relatedId(typeName, object), object))
                .toList();
    }

    private void addEdge(String edgeType, String from, String to, TraversalState traversalState) {
        String signature = edgeType + "|" + from + "|" + to;
        if (traversalState.edgeKeys().add(signature)) {
            traversalState.edges().add(new GraphEdge(edgeType, from, to));
        }
    }

    private GraphNode graphNode(String type, String id, Map<String, Object> properties) {
        OntologySchema schema = schemaMetaRepository.getSchema();
        Map<String, Object> enriched = new LinkedHashMap<>(properties);
        enriched.putAll(functionEngine.computeDerived(type, properties, schema));
        return new GraphNode(
                nodeKey(type, id),
                type,
                id,
                type + " " + id,
                enriched
        );
    }

    private String nodeKey(String type, String id) {
        return type + ":" + id;
    }

    private int clampDepth(int maxDepth) {
        return Math.min(Math.max(maxDepth, 0), MAX_DEPTH_CAP);
    }

    private String relatedId(String typeName, Map<String, Object> object) {
        String primaryKey = schemaMetaRepository.getSchema().requireObjectType(typeName).primaryKey();
        return String.valueOf(object.get(primaryKey));
    }

    private record RelatedObject(
            String type,
            String id,
            Map<String, Object> payload
    ) {
    }

    private record TraversalState(
            LinkedHashMap<String, GraphNode> nodes,
            List<GraphEdge> edges,
            Set<String> edgeKeys,
            Set<String> visited
    ) {
        private TraversalState() {
            this(new LinkedHashMap<>(), new ArrayList<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }
    }
}
