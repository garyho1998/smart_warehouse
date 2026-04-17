package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.ActionTypeDef;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import com.warehouse.ontology.support.NotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionExecutor {

    private static final Pattern IN_PRECONDITION =
            Pattern.compile("^([A-Za-z][A-Za-z0-9]*)\\s+in\\s+\\[(.*)]$");

    private final SchemaMetaRepository schemaMetaRepository;
    private final GenericRepository genericRepository;
    private final ActionDefinitionParser actionDefinitionParser;
    private final ActionValueResolver actionValueResolver;
    private final SideEffectExecutor sideEffectExecutor;
    private final AuditService auditService;

    public ActionExecutor(
            SchemaMetaRepository schemaMetaRepository,
            GenericRepository genericRepository,
            ActionDefinitionParser actionDefinitionParser,
            ActionValueResolver actionValueResolver,
            SideEffectExecutor sideEffectExecutor,
            AuditService auditService
    ) {
        this.schemaMetaRepository = schemaMetaRepository;
        this.genericRepository = genericRepository;
        this.actionDefinitionParser = actionDefinitionParser;
        this.actionValueResolver = actionValueResolver;
        this.sideEffectExecutor = sideEffectExecutor;
        this.auditService = auditService;
    }

    @Transactional
    public ActionResult execute(String actionName, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        ActionTypeDef actionTypeDef = requireActionType(actionName);
        Map<String, ActionParameterDef> parameters = actionDefinitionParser.parseParameters(actionTypeDef.parametersJson());
        validateParameters(parameters, safePayload);

        if ("CREATE".equalsIgnoreCase(actionTypeDef.mode())) {
            return executeCreate(actionName, actionTypeDef, safePayload);
        }
        return executeUpdate(actionName, actionTypeDef, safePayload);
    }

    private ActionResult executeUpdate(
            String actionName, ActionTypeDef actionTypeDef, Map<String, Object> safePayload
    ) {
        String objectIdKey = sourceObjectIdKey(actionTypeDef.objectTypeId());
        Object rawObjectId = safePayload.get(objectIdKey);
        if (rawObjectId == null || String.valueOf(rawObjectId).isBlank()) {
            throw new IllegalArgumentException("Missing required action parameter '" + objectIdKey + "'");
        }

        String objectId = String.valueOf(rawObjectId);
        Map<String, Object> beforeState = genericRepository.findById(actionTypeDef.objectTypeId(), objectId)
                .orElseThrow(() -> new NotFoundException(
                        "Unknown object instance: " + actionTypeDef.objectTypeId() + ":" + objectId
                ));

        validatePreconditions(actionTypeDef, beforeState);
        LinkedHashMap<String, Object> mutationSet = resolveMutations(actionTypeDef, safePayload);
        if (!mutationSet.isEmpty()) {
            genericRepository.update(actionTypeDef.objectTypeId(), objectId, mutationSet);
        }

        Map<String, Object> afterState = genericRepository.findById(actionTypeDef.objectTypeId(), objectId)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to reload updated object: " + actionTypeDef.objectTypeId() + ":" + objectId
                ));

        List<ActionSideEffectResult> sideEffects = sideEffectExecutor.execute(
                actionDefinitionParser.parseSideEffects(actionTypeDef.sideEffectsJson()),
                afterState,
                safePayload
        );

        if (actionTypeDef.audit()) {
            auditService.recordSuccessfulAction(
                    actionName, actionTypeDef.objectTypeId(), objectId,
                    safeString(safePayload.get("actor")),
                    safePayload, beforeState, afterState, sideEffects
            );
        }

        return new ActionResult(actionName, actionTypeDef.objectTypeId(), objectId, afterState, sideEffects);
    }

    private ActionResult executeCreate(
            String actionName, ActionTypeDef actionTypeDef, Map<String, Object> safePayload
    ) {
        LinkedHashMap<String, Object> mutationSet = resolveMutations(actionTypeDef, safePayload);
        String objectId = genericRepository.insert(actionTypeDef.objectTypeId(), mutationSet);

        Map<String, Object> afterState = genericRepository.findById(actionTypeDef.objectTypeId(), objectId)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to load created object: " + actionTypeDef.objectTypeId() + ":" + objectId
                ));

        List<ActionSideEffectResult> sideEffects = sideEffectExecutor.execute(
                actionDefinitionParser.parseSideEffects(actionTypeDef.sideEffectsJson()),
                afterState,
                safePayload
        );

        if (actionTypeDef.audit()) {
            auditService.recordSuccessfulAction(
                    actionName, actionTypeDef.objectTypeId(), objectId,
                    safeString(safePayload.get("actor")),
                    safePayload, Map.of(), afterState, sideEffects
            );
        }

        return new ActionResult(actionName, actionTypeDef.objectTypeId(), objectId, afterState, sideEffects);
    }

    private ActionTypeDef requireActionType(String actionName) {
        ActionTypeDef actionTypeDef = schemaMetaRepository.getSchema().actionTypes().get(actionName);
        if (actionTypeDef == null) {
            throw new IllegalArgumentException("Unknown action type: " + actionName);
        }
        return actionTypeDef;
    }

    private void validateParameters(Map<String, ActionParameterDef> parameters, Map<String, Object> payload) {
        parameters.forEach((name, parameterDef) -> {
            if (parameterDef.required()) {
                Object value = payload.get(name);
                if (value == null || String.valueOf(value).isBlank()) {
                    throw new IllegalArgumentException("Missing required action parameter '" + name + "'");
                }
            }
        });
    }

    private void validatePreconditions(ActionTypeDef actionTypeDef, Map<String, Object> sourceObject) {
        for (String precondition : actionDefinitionParser.parsePreconditions(actionTypeDef.preconditionsJson())) {
            Matcher matcher = IN_PRECONDITION.matcher(precondition.trim());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unsupported action precondition: " + precondition);
            }

            String propertyName = matcher.group(1);
            if (!sourceObject.containsKey(propertyName)) {
                throw new IllegalArgumentException("Unknown precondition property '" + propertyName + "'");
            }

            List<String> allowedValues = List.of(matcher.group(2).split(",")).stream()
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .toList();
            String actualValue = safeString(sourceObject.get(propertyName));
            if (!allowedValues.contains(actualValue)) {
                throw new IllegalArgumentException("Action precondition failed: " + propertyName
                        + " must be in " + allowedValues);
            }
        }
    }

    private LinkedHashMap<String, Object> resolveMutations(ActionTypeDef actionTypeDef, Map<String, Object> payload) {
        LinkedHashMap<String, Object> mutations = new LinkedHashMap<>();
        for (ActionMutationDef mutationDef : actionDefinitionParser.parseMutations(actionTypeDef.mutationsJson())) {
            mutations.putAll(actionValueResolver.resolveSet(mutationDef.set(), payload));
        }
        return mutations;
    }

    private String sourceObjectIdKey(String objectTypeId) {
        return Character.toLowerCase(objectTypeId.charAt(0)) + objectTypeId.substring(1) + "Id";
    }

    private String safeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
