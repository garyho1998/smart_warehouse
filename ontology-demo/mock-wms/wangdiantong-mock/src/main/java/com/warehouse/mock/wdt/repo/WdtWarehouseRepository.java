package com.warehouse.mock.wdt.repo;

import com.warehouse.mock.wdt.entity.WdtWarehouse;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WdtWarehouseRepository extends JpaRepository<WdtWarehouse, String> {
    List<WdtWarehouse> findByModifiedAtAfter(Instant since);
}
