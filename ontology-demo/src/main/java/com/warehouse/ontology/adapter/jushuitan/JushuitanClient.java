package com.warehouse.ontology.adapter.jushuitan;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Client for real JST open API.
 * Auth: MD5 signature over ASCII-sorted params, sent as form-encoded body.
 * Biz params are nested as JSON in the `biz` field.
 * Ref: https://openweb.jushuitan.com/doc?docId=30
 */
@Component
public class JushuitanClient {

    private static final Logger log = LoggerFactory.getLogger(JushuitanClient.class);
    private static final int PAGE_SIZE = 50;
    // JST timestamps: "yyyy-MM-dd HH:mm:ss" in UTC; inventory window max 7 days
    private static final DateTimeFormatter JST_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final long MAX_WINDOW_SECONDS = 6 * 24 * 3600L; // 6 days (safe < 7)

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String appKey;
    private final String appSecret;
    private final String accessToken;

    public JushuitanClient(RestClient.Builder builder,
                           @Value("${wms.jst.base-url:http://localhost:9002}") String baseUrl,
                           @Value("${wms.jst.app-key:demo}") String appKey,
                           @Value("${wms.jst.app-secret:demo}") String appSecret,
                           @Value("${wms.jst.access-token:demo}") String accessToken) {
        this.http = builder.baseUrl(baseUrl).build();
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.accessToken = accessToken;
    }

    public List<JstWarehouseDto> queryPartners(Instant since) {
        // Real JST: returns list under "datas" key
        Map<String, Object> body = post("/open/wms/partner/query", Map.of());
        return extractList(body, "datas", new TypeReference<>() {});
    }

    public List<JstLocationDto> querySlots(Instant since) {
        // Real sandbox doesn't grant /open/slot/query — return empty
        return List.of();
    }

    public List<JstSkuDto> querySkus(Instant since) {
        List<JstSkuDto> all = new ArrayList<>();
        Instant end = Instant.now();
        Instant begin = (since == null || since.equals(Instant.EPOCH))
                ? end.minusSeconds(MAX_WINDOW_SECONDS)
                : since;
        if (end.getEpochSecond() - begin.getEpochSecond() > MAX_WINDOW_SECONDS) {
            begin = end.minusSeconds(MAX_WINDOW_SECONDS);
        }
        int page = 0;
        while (true) {
            Map<String, Object> biz = new HashMap<>();
            biz.put("page_index", page);
            biz.put("page_size", PAGE_SIZE);
            biz.put("modified_begin", JST_FMT.format(begin));
            biz.put("modified_end",   JST_FMT.format(end));
            Map<String, Object> body = post("/open/sku/query", biz);
            List<JstSkuDto> page_items = extractList(body, "datas", new TypeReference<>() {});
            all.addAll(page_items);
            Map<String, Object> data = asMap(body.get("data"));
            if (data == null || !Boolean.TRUE.equals(data.get("has_next"))) {
                break;
            }
            page++;
        }
        return all;
    }

    public List<JstInventoryDto> queryInventory(Instant since) {
        List<JstInventoryDto> all = new ArrayList<>();
        // Real JST: max 7-day window; begin+end both required
        Instant end = Instant.now();
        Instant begin = (since == null || since.equals(Instant.EPOCH))
                ? end.minusSeconds(MAX_WINDOW_SECONDS)
                : since;
        // Clamp to 6-day window
        if (end.getEpochSecond() - begin.getEpochSecond() > MAX_WINDOW_SECONDS) {
            begin = end.minusSeconds(MAX_WINDOW_SECONDS);
        }
        int page = 0;
        while (true) {
            Map<String, Object> biz = new HashMap<>();
            biz.put("page_index", page);
            biz.put("page_size", PAGE_SIZE);
            biz.put("modified_begin", JST_FMT.format(begin));
            biz.put("modified_end",   JST_FMT.format(end));
            Map<String, Object> body = post("/open/inventory/query", biz);
            // Real JST: list is under "inventorys" key
            all.addAll(extractList(body, "inventorys", new TypeReference<List<JstInventoryDto>>() {}));
            Map<String, Object> data = asMap(body.get("data"));
            if (data == null || !Boolean.TRUE.equals(data.get("has_next"))) {
                break;
            }
            page++;
        }
        return all;
    }

    // --- Signing + transport ---

    private Map<String, Object> post(String path, Map<String, Object> bizParams) {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String bizJson = toJson(bizParams);

        // All params that enter the sign computation (ASCII-sorted)
        TreeMap<String, String> params = new TreeMap<>();
        params.put("app_key", appKey);
        params.put("access_token", accessToken);
        params.put("timestamp", timestamp);
        params.put("version", "2");
        params.put("charset", "utf-8");
        params.put("biz", bizJson);

        String sign = md5Sign(appSecret, params);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        form.add("sign", sign);

        log.debug("JST POST {} biz={}", path, bizJson);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = http.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        return result == null ? Map.of() : result;
    }

    /** app_secret + key1value1key2value2... (ASCII-sorted keys), then MD5 uppercase */
    private String md5Sign(String secret, TreeMap<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder(secret);
        sortedParams.forEach((k, v) -> sb.append(k).append(v));
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);  // JST requires lowercase MD5
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private String toJson(Map<String, Object> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    private <T> List<T> extractList(Map<String, Object> body, String key, TypeReference<List<T>> typeRef) {
        if (body != null) {
            Object code = body.get("code");
            Object msg  = body.get("msg");
            log.debug("JST response code={} msg={}", code, msg);
        }
        Map<String, Object> data = asMap(body == null ? null : body.get("data"));
        Object raw = data == null ? null : data.get(key);
        if (raw == null) return List.of();
        return mapper.convertValue(raw, typeRef);
    }
}
