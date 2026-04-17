package com.warehouse.mock.jst.api;

import com.warehouse.mock.jst.entity.JstLocation;
import com.warehouse.mock.jst.repo.JstLocationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/slot")
public class JstSlotController {

    private final JstLocationRepository repo;

    public JstSlotController(JstLocationRepository repo) {
        this.repo = repo;
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> query(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> req = body == null ? Map.of() : body;
        Instant since = req.get("modified_begin") == null ? Instant.EPOCH : Instant.parse((String) req.get("modified_begin"));
        List<JstLocation> slots = since.equals(Instant.EPOCH)
                ? repo.findAll()
                : repo.findByModifiedTimeAfter(since);
        return Map.of("code", 0, "msg", "ok", "data", Map.of("slots", slots));
    }
}
