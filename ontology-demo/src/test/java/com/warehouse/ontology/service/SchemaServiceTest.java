package com.warehouse.ontology.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.ontology.OntologyDemoApplication;
import com.warehouse.ontology.engine.GenericRepository;
import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.PropertyDef;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(classes = OntologyDemoApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SchemaServiceTest {

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private SchemaMetaRepository schemaMetaRepository;

    @Autowired
    private GenericRepository genericRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsRuntimeTypeAndMakesCrudImmediatelyAvailable() {
        ObjectTypeDef created = schemaService.createType(
                new ObjectTypeDef("Carrier", "承運商", "id", Map.of()),
                List.of(
                        new PropertyDef(null, "Carrier", "id", "string", true, false, null, List.of()),
                        new PropertyDef(null, "Carrier", "code", "string", true, true, null, List.of()),
                        new PropertyDef(null, "Carrier", "name", "string", true, false, null, List.of())
                )
        );

        assertThat(created.id()).isEqualTo("Carrier");
        assertThat(schemaMetaRepository.getSchema().objectTypes()).containsKey("Carrier");
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'carrier'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM schema_history WHERE table_name = 'object_type_def' AND record_id = 'Carrier'"
        )).isEqualTo(1);

        genericRepository.insert("Carrier", Map.of(
                "id", "CAR-001",
                "code", "SF",
                "name", "SF Express"
        ));

        assertThat(genericRepository.findById("Carrier", "CAR-001")).isPresent();
    }

    @Test
    void addsPropertyAndAltersExistingTable() {
        PropertyDef propertyDef = schemaService.addProperty(
                "Task",
                new PropertyDef(null, "Task", "waveCode", "string", false, false, null, List.of())
        );

        assertThat(propertyDef.name()).isEqualTo("waveCode");
        assertThat(schemaMetaRepository.getSchema().requireObjectType("Task").properties()).containsKey("waveCode");
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'task' "
                        + "AND LOWER(COLUMN_NAME) = 'wave_code'"
        )).isEqualTo(1);

        genericRepository.update("Task", "TSK-003", Map.of("waveCode", "WAVE-01"));
        assertThat(genericRepository.findById("Task", "TSK-003").orElseThrow().get("waveCode")).isEqualTo("WAVE-01");
        assertThat(count(
                "SELECT COUNT(*) FROM schema_history WHERE table_name = 'property_def' AND record_id = 'Task:waveCode'"
        )).isEqualTo(1);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
