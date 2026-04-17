package com.warehouse.mock.jst.repo;

import com.warehouse.mock.jst.entity.JstWarehouse;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JstWarehouseRepository extends JpaRepository<JstWarehouse, String> {
    List<JstWarehouse> findByModifiedTimeAfter(Instant since);
}
