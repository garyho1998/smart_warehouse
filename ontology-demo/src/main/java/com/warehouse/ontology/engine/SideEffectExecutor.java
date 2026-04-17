package com.warehouse.ontology.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SideEffectExecutor {

    private final GenericRepository genericRepository;
    private final ActionValueResolver actionValueResolver;

    public SideEffectExecutor(GenericRepository genericRepository, ActionValueResolver actionValueResolver) {
        this.genericRepository = genericRepository;
        this.actionValueResolver = actionValueResolver;
    }

    public List<ActionSideEffectResult> execute(List<SideEffectDef> effects, Map<String, Object> sourceObject) {
        List<ActionSideEffectResult> results = new ArrayList<>();
        for (SideEffectDef effect : effects) {
            String targetId = targetId(effect.via(), sourceObject);
            Map<String, Object> beforeState = genericRepository.findById(effect.target(), targetId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Missing target for side effect: " + effect.target() + ":" + targetId
                    ));

            genericRepository.update(effect.target(), targetId, actionValueResolver.resolveSet(effect.set()));
            Map<String, Object> afterState = genericRepository.findById(effect.target(), targetId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unable to reload target after side effect: " + effect.target() + ":" + targetId
                    ));

            results.add(new ActionSideEffectResult(effect.target(), targetId, beforeState, afterState));
        }
        return List.copyOf(results);
    }

    private String targetId(String via, Map<String, Object> sourceObject) {
        Object value = sourceObject.get(via);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Missing target for side effect via '" + via + "'");
        }
        return String.valueOf(value);
    }
}
