package com.mora.backend.repository;

import com.mora.backend.model.entity.UserAiDailyUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserAiDailyUsageRepository extends JpaRepository<UserAiDailyUsage, Long> {
    Optional<UserAiDailyUsage> findByUserIdAndModelNameAndUsageDate(Long userId, String modelName, LocalDate usageDate);
    List<UserAiDailyUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
    List<UserAiDailyUsage> findByUserId(Long userId);
}
