package com.warehouse.ontology.bootstrap;

import com.warehouse.ontology.engine.DataTableSyncer;
import com.warehouse.ontology.engine.MetaTableInitializer;
import com.warehouse.ontology.engine.SeedDataLoader;
import com.warehouse.ontology.schema.SchemaBootstrap;
import com.warehouse.ontology.schema.SchemaMetaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class OntologyStartupRunner implements ApplicationRunner {

    private final MetaTableInitializer metaTableInitializer;
    private final SchemaBootstrap schemaBootstrap;
    private final SchemaMetaRepository schemaMetaRepository;
    private final DataTableSyncer dataTableSyncer;
    private final SeedDataLoader seedDataLoader;

    public OntologyStartupRunner(
            MetaTableInitializer metaTableInitializer,
            SchemaBootstrap schemaBootstrap,
            SchemaMetaRepository schemaMetaRepository,
            DataTableSyncer dataTableSyncer,
            SeedDataLoader seedDataLoader
    ) {
        this.metaTableInitializer = metaTableInitializer;
        this.schemaBootstrap = schemaBootstrap;
        this.schemaMetaRepository = schemaMetaRepository;
        this.dataTableSyncer = dataTableSyncer;
        this.seedDataLoader = seedDataLoader;
    }

    @Override
    public void run(ApplicationArguments args) {
        metaTableInitializer.initialize();
        schemaBootstrap.seedIfEmpty();
        dataTableSyncer.syncAll(schemaMetaRepository.getSchema());
        seedDataLoader.seedIfEmpty();
    }
}
