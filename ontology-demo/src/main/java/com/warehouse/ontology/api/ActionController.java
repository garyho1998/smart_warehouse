package com.warehouse.ontology.api;

import com.warehouse.ontology.engine.ActionExecutor;
import com.warehouse.ontology.engine.ActionResult;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/actions")
public class ActionController {

    private final ActionExecutor actionExecutor;

    public ActionController(ActionExecutor actionExecutor) {
        this.actionExecutor = actionExecutor;
    }

    @PostMapping("/{actionName}")
    public ActionResult execute(
            @PathVariable String actionName,
            @RequestBody(required = false) Map<String, Object> payload
    ) {
        return actionExecutor.execute(actionName, payload);
    }
}
