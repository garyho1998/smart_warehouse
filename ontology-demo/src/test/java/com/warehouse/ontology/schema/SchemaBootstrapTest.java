package com.warehouse.ontology.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.ontology.OntologyDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OntologyDemoApplication.class)
class SchemaBootstrapTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsMetaTablesFromOntologyYaml() {
        assertThat(count("object_type_def")).isEqualTo(9);
        assertThat(count("link_type_def")).isEqualTo(12);
        assertThat(count("action_type_def")).isEqualTo(1);
        assertThat(count("schema_history")).isGreaterThan(0);
    }

    private long count(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count == null ? 0L : count;
    }
}
