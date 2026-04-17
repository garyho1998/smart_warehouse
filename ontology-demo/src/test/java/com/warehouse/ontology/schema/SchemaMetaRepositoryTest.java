package com.warehouse.ontology.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.ontology.OntologyDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = OntologyDemoApplication.class)
class SchemaMetaRepositoryTest {

    @Autowired
    private SchemaMetaRepository schemaMetaRepository;

    @Test
    void loadsOntologySchemaFromMetaTables() {
        OntologySchema schema = schemaMetaRepository.getSchema();

        assertThat(schema.objectTypes()).containsKeys("Warehouse", "Task", "Robot");
        assertThat(schema.requireObjectType("Task").properties()).containsKeys("robotId", "status", "completedAt");
        assertThat(schema.linkTypes()).containsKey("task_assigned_to_robot");
        assertThat(schema.actionTypes()).containsKey("completeTask");
    }

    @Test
    void cachesSchemaUntilInvalidated() {
        OntologySchema first = schemaMetaRepository.getSchema();
        OntologySchema second = schemaMetaRepository.getSchema();

        assertThat(second).isSameAs(first);

        schemaMetaRepository.invalidateCache();

        OntologySchema refreshed = schemaMetaRepository.getSchema();
        assertThat(refreshed).isNotSameAs(first);
    }
}
