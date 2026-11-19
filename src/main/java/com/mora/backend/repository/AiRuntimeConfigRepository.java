package com.mora.backend.repository;

import com.mora.backend.model.entity.AiRuntimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiRuntimeConfigRepository extends JpaRepository<AiRuntimeConfig, Long> {
    @Query(value = "select cast(pg_advisory_xact_lock(1836020321, 1) as text)", nativeQuery = true)
    String lockSettingsUpdate();
}
