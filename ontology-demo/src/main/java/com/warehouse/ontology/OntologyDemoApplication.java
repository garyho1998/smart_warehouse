package com.warehouse.ontology;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OntologyDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OntologyDemoApplication.class, args);
    }
}
