package com.warehouse.mock.wdt.api;

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
class WdtStockControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void stock_query_respects_start_time_filter() throws Exception {
        Instant future = Instant.now().plus(Duration.ofDays(1));
        mvc.perform(post("/openapi2/stock_query.php")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sid", "demo")
                        .param("appkey", "demo-key")
                        .param("start_time", future.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks").isEmpty());
    }

    @Test
    void stock_query_no_filter_returns_all() throws Exception {
        mvc.perform(post("/openapi2/stock_query.php")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sid", "demo")
                        .param("appkey", "demo-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stocks.length()").value(3));
    }
}
