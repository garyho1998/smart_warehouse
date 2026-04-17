package com.warehouse.ontology.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void recordSuccessfulAction(
            String actionName,
            String objectType,
            String objectId,
            String actor,
            Map<String, Object> requestPayload,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            List<ActionSideEffectResult> sideEffects
    ) {
        jdbcTemplate.update(
                "INSERT INTO audit_trail (action_name, object_type, object_id, actor, request_payload, before_state, "
                        + "after_state, side_effects) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                actionName,
                objectType,
                objectId,
                actor,
                toJson(requestPayload),
                toJson(beforeState),
                toJson(afterState),
                toJson(sideEffects)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit payload", exception);
        }
    }
}
