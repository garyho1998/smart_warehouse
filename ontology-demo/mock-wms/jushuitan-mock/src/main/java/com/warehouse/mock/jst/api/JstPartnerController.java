package com.warehouse.mock.jst.api;

import com.warehouse.mock.jst.entity.JstWarehouse;
import com.warehouse.mock.jst.repo.JstWarehouseRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/wms/partner")
public class JstPartnerController {

    private final JstWarehouseRepository repo;

    public JstPartnerController(JstWarehouseRepository repo) {
        this.repo = repo;
    }

    @PostMapping(path = "/query", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> query(@RequestBody(required = false) Map<String, Object> body) {
        List<JstWarehouse> partners = repo.findAll();
        return Map.of(
                "code", 0,
                "msg", "ok",
                "data", Map.of("partners", partners)
        );
    }
}
