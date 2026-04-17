package com.warehouse.ontology.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.ontology.OntologyDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OntologyDemoApplication.class)
class DataTableSyncerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsSeededDataTablesAndConstraints() {
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'warehouse'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = 'task'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE LOWER(TABLE_NAME) = 'task' "
                        + "AND LOWER(COLUMN_NAME) = 'robot_id'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE LOWER(TABLE_NAME) = 'task' "
                        + "AND LOWER(INDEX_NAME) = 'idx_task_robot_id'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE LOWER(TABLE_NAME) = 'task' "
                        + "AND LOWER(CONSTRAINT_NAME) = 'fk_task_assigned_to_robot'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE LOWER(TABLE_NAME) = 'zone' "
                        + "AND LOWER(INDEX_NAME) = 'idx_zone_warehouse_id'"
        )).isEqualTo(1);
        assertThat(count(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE LOWER(TABLE_NAME) = 'zone' "
                        + "AND LOWER(CONSTRAINT_NAME) = 'fk_warehouse_has_zones'"
        )).isEqualTo(1);
    }

    private long count(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }
}
