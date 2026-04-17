package com.warehouse.ontology.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.warehouse.ontology.OntologyDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OntologyDemoApplication.class)
class SeedDataIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startupLoadsExpectedSeedScenario() {
        assertThat(count("warehouse")).isEqualTo(1);
        assertThat(count("zone")).isEqualTo(4);
        assertThat(count("location")).isEqualTo(16);
        assertThat(count("sku")).isEqualTo(6);
        assertThat(count("inventory")).isEqualTo(10);
        assertThat(count("robot")).isEqualTo(2);
        assertThat(count("warehouse_order")).isEqualTo(3);
        assertThat(count("task")).isEqualTo(5);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM task WHERE id = 'TSK-005'",
                String.class
        )).isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT battery_pct FROM robot WHERE id = 'R-GEK-001'",
                Integer.class
        )).isEqualTo(15);
    }

    private long count(String tableName) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
        return count == null ? 0L : count;
    }
}
