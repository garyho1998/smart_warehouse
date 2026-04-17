package com.warehouse.mock.jst.api;

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
class JstPartnerControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void partner_query_returns_warehouses_in_json() throws Exception {
        mvc.perform(post("/open/wms/partner/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"app_key\":\"demo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.partners[0].wms_co_id").value("JST-WH-001"))
                .andExpect(jsonPath("$.data.partners[0].name").value("上海自营仓"));
    }
}
