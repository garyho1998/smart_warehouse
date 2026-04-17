package com.warehouse.ontology.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.warehouse.ontology.OntologyDemoApplication;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = OntologyDemoApplication.class)
@Transactional
class GenericRepositoryTest {

    @Autowired
    private GenericRepository genericRepository;

    @Test
    void insertsValidTaskAndAppliesDefaultValues() {
        String id = genericRepository.insert("Task", Map.of(
                "id", "TSK-900",
                "orderLineId", "LINE-004",
                "type", "MOVE",
                "status", "PENDING"
        ));

        Map<String, Object> inserted = genericRepository.findById("Task", id).orElseThrow();
        assertThat(inserted.get("id")).isEqualTo("TSK-900");
        assertThat(inserted.get("priority")).isEqualTo(0);
        assertThat(inserted.get("orderLineId")).isEqualTo("LINE-004");
    }

    @Test
    void insertsValidRobotAndPreservesEnumValues() {
        String id = genericRepository.insert("Robot", Map.of(
                "id", "R-SIM-900",
                "code", "R-SIM-900",
                "brand", "SIMULATED",
                "model", "SimBot",
                "status", "IDLE",
                "batteryPct", 100,
                "assignedZoneId", "ZONE-PICK"
        ));

        Map<String, Object> inserted = genericRepository.findById("Robot", id).orElseThrow();
        assertThat(inserted.get("brand")).isEqualTo("SIMULATED");
        assertThat(inserted.get("status")).isEqualTo("IDLE");
    }

    @Test
    void rejectsUnknownType() {
        assertThatThrownBy(() -> genericRepository.findAll("UnknownType"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown object type");
    }

    @Test
    void rejectsUnknownProperty() {
        assertThatThrownBy(() -> genericRepository.insert("Task", Map.of(
                "id", "TSK-901",
                "type", "MOVE",
                "status", "PENDING",
                "mysteryField", "x"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown property");
    }

    @Test
    void rejectsInvalidEnum() {
        assertThatThrownBy(() -> genericRepository.insert("Robot", Map.of(
                "id", "R-BAD-001",
                "code", "R-BAD-001",
                "brand", "BROKEN",
                "status", "IDLE"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid enum value");
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThatThrownBy(() -> genericRepository.insert("Robot", Map.of(
                "id", "R-BAD-002",
                "brand", "SIMULATED",
                "status", "IDLE"
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required property 'code'");
    }

    @Test
    void updatesAllowedFieldsSuccessfully() {
        genericRepository.update("Task", "TSK-001", Map.of(
                "status", "IN_PROGRESS",
                "priority", 95
        ));

        Map<String, Object> updated = genericRepository.findById("Task", "TSK-001").orElseThrow();
        assertThat(updated.get("status")).isEqualTo("IN_PROGRESS");
        assertThat(updated.get("priority")).isEqualTo(95);
    }

    @Test
    void rejectsPrimaryKeyUpdate() {
        assertThatThrownBy(() -> genericRepository.update("Task", "TSK-001", Map.of("id", "TSK-999")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Primary key");
    }

    @Test
    void findsFailedTasksByProperty() {
        List<Map<String, Object>> failedTasks = genericRepository.findByProperty("Task", "status", "FAILED");

        assertThat(failedTasks).extracting(row -> row.get("id")).containsExactly("TSK-005");
    }
}
