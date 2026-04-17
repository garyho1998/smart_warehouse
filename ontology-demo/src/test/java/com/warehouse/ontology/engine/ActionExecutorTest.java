package com.warehouse.ontology.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.warehouse.ontology.OntologyDemoApplication;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = OntologyDemoApplication.class)
@Transactional
class ActionExecutorTest {

    @Autowired
    private ActionExecutor actionExecutor;

    @Autowired
    private GenericRepository genericRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void completesTaskRunsSideEffectAndRecordsAudit() {
        ActionResult result = actionExecutor.execute("completeTask", Map.of(
                "taskId", "TSK-002",
                "actor", "ops-user"
        ));

        assertThat(result.objectType()).isEqualTo("Task");
        assertThat(result.objectId()).isEqualTo("TSK-002");
        assertThat(result.updatedObject().get("status")).isEqualTo("COMPLETED");
        assertThat(result.updatedObject().get("completedAt")).isNotNull();
        assertThat(result.sideEffects()).singleElement().satisfies(effect -> {
            assertThat(effect.targetType()).isEqualTo("Robot");
            assertThat(effect.targetId()).isEqualTo("R-HIK-001");
            assertThat(effect.afterState().get("status")).isEqualTo("IDLE");
        });

        assertThat(genericRepository.findById("Robot", "R-HIK-001").orElseThrow().get("status")).isEqualTo("IDLE");
        assertThat(count(
                "SELECT COUNT(*) FROM audit_trail WHERE action_name = 'completeTask' AND object_id = 'TSK-002'"
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT actor FROM audit_trail WHERE action_name = 'completeTask' AND object_id = 'TSK-002'",
                String.class
        )).isEqualTo("ops-user");
    }

    @Test
    void rejectsTaskOutsidePreconditionWithoutMutatingState() {
        assertThatThrownBy(() -> actionExecutor.execute("completeTask", Map.of(
                "taskId", "TSK-005",
                "actor", "ops-user"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Action precondition failed");

        assertThat(genericRepository.findById("Task", "TSK-005").orElseThrow().get("status")).isEqualTo("FAILED");
        assertThat(genericRepository.findById("Robot", "R-GEK-001").orElseThrow().get("status")).isEqualTo("IDLE");
        assertThat(count(
                "SELECT COUNT(*) FROM audit_trail WHERE action_name = 'completeTask' AND object_id = 'TSK-005'"
        )).isEqualTo(0);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
