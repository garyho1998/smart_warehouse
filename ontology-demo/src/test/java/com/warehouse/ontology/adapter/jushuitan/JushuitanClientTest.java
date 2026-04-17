package com.warehouse.ontology.adapter.jushuitan;

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

@RestClientTest(JushuitanClient.class)
class JushuitanClientTest {

    @Autowired
    JushuitanClient client;

    @Autowired
    MockRestServiceServer server;

    @Test
    void fetches_partners() {
        server.expect(requestTo("http://localhost:9002/open/wms/partner/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"msg":"ok","data":{"partners":[
                          {"wms_co_id":"JST-WH-001","name":"上海自营仓","modified_time":"2026-04-17T00:00:00Z"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var result = client.queryPartners(Instant.EPOCH);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).wmsCoId()).isEqualTo("JST-WH-001");
    }

    @Test
    void fetches_slots() {
        server.expect(requestTo("http://localhost:9002/open/slot/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"slots":[
                          {"slot_id":"SH_R1_S1","wms_co_id":"JST-WH-001","area":"pick","floor":1,"enabled":true,"modified_time":"2026-04-17T00:00:00Z"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var result = client.querySlots(Instant.EPOCH);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).area()).isEqualTo("pick");
    }

    @Test
    void fetches_skus() {
        server.expect(requestTo("http://localhost:9002/open/sku/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"skus":[
                          {"sku_id":"JST-SKU-001","name":"行動電源","modified_time":"2026-04-17T00:00:00Z"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        var result = client.querySkus(Instant.EPOCH);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).skuId()).isEqualTo("JST-SKU-001");
    }

    @Test
    void inventory_auto_paginates_until_has_next_false() {
        // Page 0: has_next=true
        server.expect(requestTo("http://localhost:9002/open/inventory/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"items":[
                          {"id":1,"sku_id":"S1","slot_id":"L1","wms_co_id":"W1","qty":10,"lock_qty":0,"modified_time":"2026-04-17T00:00:00Z"}
                        ],"has_next":true,"page_index":0}}
                        """, MediaType.APPLICATION_JSON));
        // Page 1: has_next=false
        server.expect(requestTo("http://localhost:9002/open/inventory/query"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"items":[
                          {"id":2,"sku_id":"S2","slot_id":"L2","wms_co_id":"W1","qty":20,"lock_qty":0,"modified_time":"2026-04-17T00:00:00Z"}
                        ],"has_next":false,"page_index":1}}
                        """, MediaType.APPLICATION_JSON));

        var result = client.queryInventory(Instant.EPOCH);
        assertThat(result).hasSize(2);
        assertThat(result).extracting(JstInventoryDto::skuId).containsExactly("S1", "S2");
    }
}
