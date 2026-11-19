package com.mora.backend.repository;

import com.mora.backend.model.entity.AiUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, Long> {
    List<AiUsageEvent> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(LocalDateTime from);
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
    Optional<AiUsageEvent> findFirstByOrderByCreatedAtAsc();
}
