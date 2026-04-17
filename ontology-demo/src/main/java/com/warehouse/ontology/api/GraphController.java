package com.warehouse.ontology.api;

import com.warehouse.ontology.engine.GraphResult;
import com.warehouse.ontology.engine.GraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/traverse/{type}/{id}")
    public GraphResult traverse(
            @PathVariable String type,
            @PathVariable String id,
            @RequestParam(name = "depth", defaultValue = "3") int depth
    ) {
        return graphService.traverse(type, id, depth);
    }

    @GetMapping("/trace-anomaly/{type}/{id}")
    public GraphResult traceAnomaly(@PathVariable String type, @PathVariable String id) {
        return graphService.traceAnomaly(type, id);
    }
}
