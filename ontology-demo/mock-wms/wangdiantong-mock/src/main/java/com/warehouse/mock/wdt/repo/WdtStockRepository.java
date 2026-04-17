package com.warehouse.mock.wdt.repo;

import com.warehouse.mock.wdt.entity.WdtStock;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WdtStockRepository extends JpaRepository<WdtStock, Long> {
    List<WdtStock> findByModifiedAtAfter(Instant since);
}
