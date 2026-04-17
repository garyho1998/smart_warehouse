package com.warehouse.mock.jst.repo;

import com.warehouse.mock.jst.entity.JstSku;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JstSkuRepository extends JpaRepository<JstSku, String> {
    List<JstSku> findByModifiedTimeAfter(Instant since);
}
