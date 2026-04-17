package com.warehouse.ontology.adapter.wangdiantong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

@RestClientTest(WangdiantongClient.class)
class WangdiantongClientTest {

    @Autowired
    WangdiantongClient client;

    @Autowired
    MockRestServiceServer server;

    @Test
    void fetches_and_deserialises_stock() {
        server.expect(requestTo("http://localhost:9001/openapi2/stock_query.php"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":"0","message":"ok","stocks":[
                          {"spec_no":"WDT-SPEC-001","bin_code":"A-01-01","stock_num":200,"lock_num":10,"modified_at":"2026-04-17T00:00:00Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var stocks = client.queryStock(Instant.EPOCH);
        assertThat(stocks).hasSize(1);
        assertThat(stocks.get(0).binCode()).isEqualTo("A-01-01");
        assertThat(stocks.get(0).specNo()).isEqualTo("WDT-SPEC-001");
        assertThat(stocks.get(0).stockNum()).isEqualTo(200);
    }

    @Test
    void fetches_and_deserialises_warehouses() {
        server.expect(requestTo("http://localhost:9001/openapi2/warehouse_query.php"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":"0","warehouses":[
                          {"warehouse_no":"WDT-SZ-001","warehouse_name":"深圳自营仓","is_main":true,"modified_at":"2026-04-17T00:00:00Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var warehouses = client.queryWarehouses(Instant.EPOCH);
        assertThat(warehouses).hasSize(1);
        assertThat(warehouses.get(0).warehouseNo()).isEqualTo("WDT-SZ-001");
    }

    @Test
    void fetches_and_deserialises_locations() {
        server.expect(requestTo("http://localhost:9001/openapi2/location_query.php"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":"0","locations":[
                          {"bin_code":"A-01-01","warehouse_no":"WDT-SZ-001","zone_name":"揀貨區","status":"ACTIVE","modified_at":"2026-04-17T00:00:00Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var locs = client.queryLocations(Instant.EPOCH);
        assertThat(locs).hasSize(1);
        assertThat(locs.get(0).zoneName()).isEqualTo("揀貨區");
    }

    @Test
    void fetches_and_deserialises_skus() {
        server.expect(requestTo("http://localhost:9001/openapi2/goods_query.php"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":"0","goods":[
                          {"spec_no":"WDT-SPEC-001","goods_no":"WDT-G-001","goods_name":"USB-C線 1m","unit":"件","modified_at":"2026-04-17T00:00:00Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var skus = client.querySkus(Instant.EPOCH);
        assertThat(skus).hasSize(1);
        assertThat(skus.get(0).specNo()).isEqualTo("WDT-SPEC-001");
    }
}
