package com.warehouse.ontology.adapter.wangdiantong;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class WangdiantongClient {

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public WangdiantongClient(RestClient.Builder builder,
                              @Value("${wms.wdt.base-url:http://localhost:9001}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    public List<WdtStockDto> queryStock(Instant since) {
        Map<String, Object> body = post("/openapi2/stock_query.php", since);
        return deserializeList(body, "stocks", new TypeReference<List<WdtStockDto>>() {});
    }

    public List<WdtWarehouseDto> queryWarehouses(Instant since) {
        Map<String, Object> body = post("/openapi2/warehouse_query.php", since);
        return deserializeList(body, "warehouses", new TypeReference<List<WdtWarehouseDto>>() {});
    }

    public List<WdtLocationDto> queryLocations(Instant since) {
        Map<String, Object> body = post("/openapi2/location_query.php", since);
        return deserializeList(body, "locations", new TypeReference<List<WdtLocationDto>>() {});
    }

    public List<WdtSkuDto> querySkus(Instant since) {
        Map<String, Object> body = post("/openapi2/goods_query.php", since);
        return deserializeList(body, "goods", new TypeReference<List<WdtSkuDto>>() {});
    }

    private Map<String, Object> post(String path, Instant since) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("sid", "demo");
        form.add("appkey", "demo-key");
        if (since != null && !Instant.EPOCH.equals(since)) {
            form.add("start_time", since.toString());
        }
        return http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private <T> List<T> deserializeList(Map<String, Object> body, String key, TypeReference<List<T>> typeRef) {
        Object raw = body == null ? null : body.get(key);
        if (raw == null) {
            return List.of();
        }
        return mapper.convertValue(raw, typeRef);
    }

    private Map<String, Object> toMap(LinkedHashMap<String, Object> form) {
        return form;
    }
}
