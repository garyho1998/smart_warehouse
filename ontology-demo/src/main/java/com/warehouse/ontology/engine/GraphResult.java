package com.warehouse.ontology.engine;

import java.util.Collections;
import java.util.List;

public record GraphResult(
        GraphNode start,
        List<GraphNode> nodes,
        List<GraphEdge> edges,
        List<GraphInsight> insights
) {

    public GraphResult {
        nodes = Collections.unmodifiableList(List.copyOf(nodes));
        edges = Collections.unmodifiableList(List.copyOf(edges));
        insights = Collections.unmodifiableList(List.copyOf(insights));
    }
}
