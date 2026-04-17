package com.warehouse.ontology.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.ontology.OntologyDemoApplication;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = OntologyDemoApplication.class)
class GraphServiceTest {

    @Autowired
    private GraphService graphService;

    @Test
    void traversesTaskGraphAcrossDirectAndInverseLinks() {
        GraphResult graphResult = graphService.traverse("Task", "TSK-005", 3);

        assertThat(graphResult.nodes()).extracting(GraphNode::type)
                .contains("Task", "Robot", "Location", "Zone", "WarehouseOrder");
        assertThat(graphResult.edges())
                .extracting(GraphEdge::type)
                .contains("task_assigned_to_robot");
    }

    @Test
    void supportsInverseTraversalWithoutDuplicatingNodes() {
        GraphResult graphResult = graphService.traverse("Zone", "ZONE-PICK", 2);

        assertThat(graphResult.nodes()).extracting(GraphNode::type)
                .contains("Robot", "Location");

        Set<String> uniqueKeys = graphResult.nodes().stream()
                .map(GraphNode::key)
                .collect(Collectors.toSet());
        assertThat(uniqueKeys).hasSameSizeAs(graphResult.nodes());
    }

    @Test
    void tracesAnomalyAndGeneratesInsights() {
        GraphResult graphResult = graphService.traceAnomaly("Task", "TSK-005");

        assertThat(graphResult.insights())
                .extracting(GraphInsight::severity, GraphInsight::message)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("HIGH", "Robot R-GEK-001 batteryPct=15 may be causing task failure"),
                        org.assertj.core.groups.Tuple.tuple("MEDIUM", "Task TSK-005 failure impacts order ORD-OUT-001")
                );
    }
}
