package com.warehouse.ontology.engine;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MetaTableInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final Resource ddlResource = new ClassPathResource("schema/meta-tables.sql");

    public MetaTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void initialize() {
        String ddl = readResource();
        Arrays.stream(ddl.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isBlank())
                .forEach(jdbcTemplate::execute);
    }

    private String readResource() {
        try {
            return new String(ddlResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read meta table DDL", exception);
        }
    }
}
