package com.warehouse.ontology.adapter.jushuitan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class JushuitanClient {

    private static final int PAGE_SIZE = 50;

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public JushuitanClient(RestClient.Builder builder,
                           @Value("${wms.jst.base-url:http://localhost:9002}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<JstWarehouseDto> queryPartners(Instant since) {
        Map<String, Object> body = postJson("/open/wms/partner/query", since == null ? Map.of() : Map.of("modified_begin", since.toString()));
        return extractList(body, "partners", new TypeReference<>() {});
    }

    public List<JstLocationDto> querySlots(Instant since) {
        Map<String, Object> body = postJson("/open/slot/query", since == null ? Map.of() : Map.of("modified_begin", since.toString()));
        return extractList(body, "slots", new TypeReference<>() {});
    }

    public List<JstSkuDto> querySkus(Instant since) {
        Map<String, Object> body = postJson("/open/sku/query", since == null ? Map.of() : Map.of("modified_begin", since.toString()));
        return extractList(body, "skus", new TypeReference<>() {});
    }

    public List<JstInventoryDto> queryInventory(Instant since) {
        List<JstInventoryDto> all = new ArrayList<>();
        int page = 0;
        while (true) {
            Map<String, Object> req = new HashMap<>();
            req.put("page_index", page);
            req.put("page_size", PAGE_SIZE);
            if (since != null && !since.equals(Instant.EPOCH)) {
                req.put("modified_begin", since.toString());
            }
            Map<String, Object> body = postJson("/open/inventory/query", req);
            all.addAll(extractList(body, "items", new TypeReference<List<JstInventoryDto>>() {}));
            Map<String, Object> data = asMap(body.get("data"));
            if (data == null || !Boolean.TRUE.equals(data.get("has_next"))) {
                break;
            }
            page++;
        }
        return all;
    }

    private Map<String, Object> postJson(String path, Map<String, Object> body) {
        return http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private <T> List<T> extractList(Map<String, Object> body, String key, TypeReference<List<T>> typeRef) {
        Map<String, Object> data = asMap(body == null ? null : body.get("data"));
        Object raw = data == null ? null : data.get(key);
        if (raw == null) return List.of();
        return mapper.convertValue(raw, typeRef);
    }
}
