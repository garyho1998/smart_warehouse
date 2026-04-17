package com.warehouse.mock.wdt.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WdtWarehouseControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void warehouse_query_returns_all_warehouses_in_wdt_format() throws Exception {
        mvc.perform(post("/openapi2/warehouse_query.php")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sid", "demo")
                        .param("appkey", "demo-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.warehouses[0].warehouse_no").value("WDT-SZ-001"))
                .andExpect(jsonPath("$.warehouses[0].warehouse_name").value("深圳自营仓"));
    }
}
