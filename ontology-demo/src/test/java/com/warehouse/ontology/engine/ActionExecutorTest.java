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

    // ── completeTask (regression) ───────────────────────────────────────

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
        assertThat(result.sideEffects()).hasSize(2);
        assertThat(result.sideEffects().get(0).targetType()).isEqualTo("Robot");
        assertThat(result.sideEffects().get(0).afterState().get("status")).isEqualTo("IDLE");
        assertThat(result.sideEffects().get(1).targetType()).isEqualTo("OrderLine");
        assertThat(result.sideEffects().get(1).afterState().get("pickedQuantity")).isEqualTo(20);

        assertThat(genericRepository.findById("Robot", "R-HIK-001").orElseThrow().get("status")).isEqualTo("IDLE");
        assertThat(genericRepository.findById("OrderLine", "LINE-003").orElseThrow().get("pickedQuantity")).isEqualTo(20);
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

    // ── assignTask ($param.xxx + side effect) ───────────────────────────

    @Test
    void assignsTaskAndSetsRobotBusy() {
        ActionResult result = actionExecutor.execute("assignTask", Map.of(
                "taskId", "TSK-003",
                "robotId", "R-GEK-001",
                "actor", "ops-user"
        ));

        assertThat(result.objectType()).isEqualTo("Task");
        assertThat(result.objectId()).isEqualTo("TSK-003");
        assertThat(result.updatedObject().get("status")).isEqualTo("ASSIGNED");
        assertThat(result.updatedObject().get("robotId")).isEqualTo("R-GEK-001");
        assertThat(result.updatedObject().get("assignedAt")).isNotNull();

        assertThat(result.sideEffects()).singleElement().satisfies(effect -> {
            assertThat(effect.targetType()).isEqualTo("Robot");
            assertThat(effect.targetId()).isEqualTo("R-GEK-001");
            assertThat(effect.afterState().get("status")).isEqualTo("BUSY");
        });

        assertThat(count(
                "SELECT COUNT(*) FROM audit_trail WHERE action_name = 'assignTask' AND object_id = 'TSK-003'"
        )).isEqualTo(1);
    }

    // ── startTask ───────────────────────────────────────────────────────

    @Test
    void startsAssignedTask() {
        // First assign TSK-003
        actionExecutor.execute("assignTask", Map.of(
                "taskId", "TSK-003", "robotId", "R-GEK-001", "actor", "ops-user"
        ));

        ActionResult result = actionExecutor.execute("startTask", Map.of(
                "taskId", "TSK-003",
                "actor", "ops-user"
        ));

        assertThat(result.updatedObject().get("status")).isEqualTo("IN_PROGRESS");
    }

    // ── cancelTask (conditional side effect) ────────────────────────────

    @Test
    void cancelsTaskWithRobotSetsRobotIdle() {
        // TSK-001 is ASSIGNED with robotId=R-HIK-001
        ActionResult result = actionExecutor.execute("cancelTask", Map.of(
                "taskId", "TSK-001",
                "actor", "ops-user"
        ));

        assertThat(result.updatedObject().get("status")).isEqualTo("CANCELLED");
        assertThat(result.sideEffects()).singleElement().satisfies(effect -> {
            assertThat(effect.targetType()).isEqualTo("Robot");
            assertThat(effect.targetId()).isEqualTo("R-HIK-001");
            assertThat(effect.afterState().get("status")).isEqualTo("IDLE");
        });
    }

    @Test
    void cancelsPendingTaskSkipsSideEffectWhenNoRobot() {
        // TSK-003 is PENDING with robotId=null
        ActionResult result = actionExecutor.execute("cancelTask", Map.of(
                "taskId", "TSK-003",
                "actor", "ops-user"
        ));

        assertThat(result.updatedObject().get("status")).isEqualTo("CANCELLED");
        assertThat(result.sideEffects()).isEmpty();
    }

    // ── createOutboundOrder (CREATE mode) ───────────────────────────────

    @Test
    void createsOutboundOrder() {
        ActionResult result = actionExecutor.execute("createOutboundOrder", Map.of(
                "id", "ORD-NEW-001",
                "warehouseId", "WH-SZ-001",
                "externalId", "EXT-20260416",
                "priority", 1,
                "actor", "ops-user"
        ));

        assertThat(result.objectType()).isEqualTo("WarehouseOrder");
        assertThat(result.objectId()).isEqualTo("ORD-NEW-001");
        assertThat(result.updatedObject().get("type")).isEqualTo("OUTBOUND");
        assertThat(result.updatedObject().get("status")).isEqualTo("PENDING");
        assertThat(result.updatedObject().get("warehouseId")).isEqualTo("WH-SZ-001");
        assertThat(result.updatedObject().get("externalId")).isEqualTo("EXT-20260416");
        assertThat(result.updatedObject().get("priority")).isEqualTo(1);
        assertThat(result.sideEffects()).isEmpty();

        assertThat(count(
                "SELECT COUNT(*) FROM audit_trail WHERE action_name = 'createOutboundOrder' AND object_id = 'ORD-NEW-001'"
        )).isEqualTo(1);
    }

    @Test
    void createsOutboundOrderWithOptionalParamsOmitted() {
        ActionResult result = actionExecutor.execute("createOutboundOrder", Map.of(
                "id", "ORD-NEW-002",
                "warehouseId", "WH-SZ-001",
                "actor", "ops-user"
        ));

        assertThat(result.updatedObject().get("type")).isEqualTo("OUTBOUND");
        assertThat(result.updatedObject().get("status")).isEqualTo("PENDING");
        assertThat(result.updatedObject().get("externalId")).isNull();
    }

    // ── Full outbound order flow (end-to-end) ─────────────────────────

    @Test
    void fullOutboundOrderFlow() {
        // Step 1: createOutboundOrder
        ActionResult order = actionExecutor.execute("createOutboundOrder", Map.of(
                "id", "ORD-FLOW-001", "warehouseId", "WH-SZ-001",
                "externalId", "EXT-FLOW", "priority", 1, "actor", "ops"
        ));
        assertThat(order.updatedObject().get("status")).isEqualTo("PENDING");

        // Step 2: addOrderLine
        ActionResult line = actionExecutor.execute("addOrderLine", Map.of(
                "id", "LINE-FLOW-001", "orderId", "ORD-FLOW-001",
                "skuId", "SKU-001", "quantity", 10, "actor", "ops"
        ));
        assertThat(line.updatedObject().get("quantity")).isEqualTo(10);
        assertThat(line.updatedObject().get("pickedQuantity")).isEqualTo(0);

        // Step 3: startOrder
        ActionResult started = actionExecutor.execute("startOrder", Map.of(
                "warehouseOrderId", "ORD-FLOW-001", "actor", "ops"
        ));
        assertThat(started.updatedObject().get("status")).isEqualTo("IN_PROGRESS");

        // Step 4: createPickTask
        ActionResult task = actionExecutor.execute("createPickTask", Map.of(
                "id", "TSK-FLOW-001", "orderLineId", "LINE-FLOW-001",
                "fromLocationId", "LOC-STORAGE-01", "toLocationId", "LOC-DOCK-01",
                "priority", 90, "actor", "ops"
        ));
        assertThat(task.updatedObject().get("status")).isEqualTo("PENDING");
        assertThat(task.updatedObject().get("type")).isEqualTo("PICK");

        // Step 5: assignTask
        actionExecutor.execute("assignTask", Map.of(
                "taskId", "TSK-FLOW-001", "robotId", "R-HIK-001", "actor", "ops"
        ));

        // Step 6: startTask
        actionExecutor.execute("startTask", Map.of(
                "taskId", "TSK-FLOW-001", "actor", "ops"
        ));

        // Step 7: completeTask → Robot IDLE + OrderLine pickedQuantity = quantity
        ActionResult completed = actionExecutor.execute("completeTask", Map.of(
                "taskId", "TSK-FLOW-001", "actor", "ops"
        ));
        assertThat(completed.updatedObject().get("status")).isEqualTo("COMPLETED");
        assertThat(genericRepository.findById("Robot", "R-HIK-001").orElseThrow().get("status")).isEqualTo("IDLE");
        assertThat(genericRepository.findById("OrderLine", "LINE-FLOW-001").orElseThrow().get("pickedQuantity")).isEqualTo(10);

        // Step 8: completeOrder
        ActionResult done = actionExecutor.execute("completeOrder", Map.of(
                "warehouseOrderId", "ORD-FLOW-001", "actor", "ops"
        ));
        assertThat(done.updatedObject().get("status")).isEqualTo("COMPLETED");
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
