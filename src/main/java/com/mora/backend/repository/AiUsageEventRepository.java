package com.mora.backend.repository;

import com.mora.backend.model.entity.AiUsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, Long> {
    List<AiUsageEvent> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(LocalDateTime from);
    long countByCreatedAtGreaterThanEqual(LocalDateTime from);
    Optional<AiUsageEvent> findFirstByOrderByCreatedAtAsc();
    @Query("SELECT COALESCE(SUM(e.totalTokens), 0) FROM AiUsageEvent e WHERE e.userId = :userId AND e.createdAt >= :from")
    long sumTokensByUserAndCreatedAtGreaterThanEqual(@Param("userId") Long userId, @Param("from") LocalDateTime from);
}
