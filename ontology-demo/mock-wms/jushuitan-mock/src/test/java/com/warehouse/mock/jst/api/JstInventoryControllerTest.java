package com.warehouse.mock.jst.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class JstInventoryControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void inventory_query_returns_all_with_has_next_false_when_small() throws Exception {
        mvc.perform(post("/open/inventory/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page_index\":0,\"page_size\":50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.has_next").value(false))
                .andExpect(jsonPath("$.data.page_index").value(0));
    }

    @Test
    void inventory_query_caps_page_size_at_50() throws Exception {
        mvc.perform(post("/open/inventory/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page_index\":0,\"page_size\":500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void inventory_query_respects_modified_begin() throws Exception {
        Instant future = Instant.now().plus(Duration.ofDays(1));
        mvc.perform(post("/open/inventory/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"page_index\":0,\"page_size\":50,\"modified_begin\":\"" + future + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
