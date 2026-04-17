package com.warehouse.mock.wdt.api;

import com.warehouse.mock.wdt.entity.WdtStock;
import com.warehouse.mock.wdt.repo.WdtStockRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi2")
public class WdtStockController {

    private final WdtStockRepository repo;

    public WdtStockController(WdtStockRepository repo) {
        this.repo = repo;
    }

    @PostMapping(path = "/stock_query.php",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> query(@RequestParam String sid,
                                     @RequestParam String appkey,
                                     @RequestParam(name = "start_time", required = false) String startTime) {
        Instant since = startTime == null ? Instant.EPOCH : Instant.parse(startTime);
        List<WdtStock> stocks = since.equals(Instant.EPOCH) ? repo.findAll() : repo.findByModifiedAtAfter(since);
        return Map.of("code", "0", "message", "success", "stocks", stocks);
    }
}
