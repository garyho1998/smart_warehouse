package com.warehouse.mock.wdt.repo;

import com.warehouse.mock.wdt.entity.WdtSku;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WdtSkuRepository extends JpaRepository<WdtSku, String> {
    List<WdtSku> findByModifiedAtAfter(Instant since);
}
