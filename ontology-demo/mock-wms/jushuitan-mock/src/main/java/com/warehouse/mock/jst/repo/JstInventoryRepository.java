package com.warehouse.mock.jst.repo;

import com.warehouse.mock.jst.entity.JstInventory;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JstInventoryRepository extends JpaRepository<JstInventory, Long> {
    Page<JstInventory> findByModifiedTimeAfter(Instant since, Pageable pageable);
}
