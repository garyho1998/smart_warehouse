package com.warehouse.mock.jst.api;

import com.warehouse.mock.jst.entity.JstInventory;
import com.warehouse.mock.jst.repo.JstInventoryRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/inventory")
public class JstInventoryController {

    private static final int MAX_PAGE_SIZE = 50;

    private final JstInventoryRepository repo;

    public JstInventoryController(JstInventoryRepository repo) {
        this.repo = repo;
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> query(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        int pageIndex = asInt(req.get("page_index"), 0);
        int pageSize = Math.min(asInt(req.get("page_size"), MAX_PAGE_SIZE), MAX_PAGE_SIZE);
        Instant since = req.get("modified_begin") == null ? Instant.EPOCH : Instant.parse((String) req.get("modified_begin"));

        Page<JstInventory> page = repo.findByModifiedTimeAfter(since, PageRequest.of(pageIndex, pageSize));

        Map<String, Object> data = new HashMap<>();
        data.put("items", page.getContent());
        data.put("has_next", page.hasNext());
        data.put("page_index", page.getNumber());
        data.put("total", page.getTotalElements());

        return Map.of("code", 0, "msg", "ok", "data", data);
    }

    private static int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        return Integer.parseInt(o.toString());
    }
}
