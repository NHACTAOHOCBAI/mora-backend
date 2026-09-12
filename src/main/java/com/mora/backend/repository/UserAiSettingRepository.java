package com.mora.backend.repository;

import com.mora.backend.model.entity.UserAiSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAiSettingRepository extends JpaRepository<UserAiSetting, Long> {
    Optional<UserAiSetting> findByUserId(Long userId);
}
