package com.warehouse.ontology.engine;

import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class SeedDataLoader {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public SeedDataLoader(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    public void seedIfEmpty() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM warehouse", Long.class);
        if (count != null && count > 0) {
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("seed-data.sql"));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
