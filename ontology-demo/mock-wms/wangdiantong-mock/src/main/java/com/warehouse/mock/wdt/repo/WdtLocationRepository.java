package com.warehouse.mock.wdt.repo;

import com.warehouse.mock.wdt.entity.WdtLocation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WdtLocationRepository extends JpaRepository<WdtLocation, String> {
    List<WdtLocation> findByModifiedAtAfter(Instant since);
}
