package com.warehouse.mock.wdt.api;

import com.warehouse.mock.wdt.entity.WdtWarehouse;
import com.warehouse.mock.wdt.repo.WdtWarehouseRepository;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi2")
public class WdtWarehouseController {

    private final WdtWarehouseRepository repo;

    public WdtWarehouseController(WdtWarehouseRepository repo) {
        this.repo = repo;
    }

    @PostMapping(path = "/warehouse_query.php",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> query(@RequestParam String sid,
                                     @RequestParam String appkey) {
        List<WdtWarehouse> all = repo.findAll();
        return Map.of("code", "0", "message", "success", "warehouses", all);
    }
}
