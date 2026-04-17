package com.warehouse.ontology.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.warehouse.ontology.OntologyDemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = OntologyDemoApplication.class, properties = {"wms.sync.interval-ms=3600000"})
@AutoConfigureMockMvc
class SourcesControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void sources_endpoint_lists_all_adapters_with_counts() throws Exception {
        mvc.perform(get("/api/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.name == 'wangdiantong')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'jushuitan')]").exists())
                .andExpect(jsonPath("$[0].counts.Warehouse").isNumber())
                .andExpect(jsonPath("$[0].counts.Location").isNumber())
                .andExpect(jsonPath("$[0].counts.Sku").isNumber())
                .andExpect(jsonPath("$[0].counts.Inventory").isNumber());
    }
}
