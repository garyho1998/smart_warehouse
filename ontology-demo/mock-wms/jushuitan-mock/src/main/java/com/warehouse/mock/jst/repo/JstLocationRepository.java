package com.warehouse.mock.jst.repo;

import com.warehouse.mock.jst.entity.JstLocation;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JstLocationRepository extends JpaRepository<JstLocation, String> {
    List<JstLocation> findByModifiedTimeAfter(Instant since);
}
