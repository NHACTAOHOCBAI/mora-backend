package com.mora.backend.repository;

import com.mora.backend.model.entity.SpaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceMemberRepository extends JpaRepository<SpaceMember, Long> {

    Optional<SpaceMember> findBySpaceIdAndUserId(Long spaceId, Long userId);

    List<SpaceMember> findBySpaceId(Long spaceId);

    List<SpaceMember> findByUserId(Long userId);

    boolean existsBySpaceIdAndUserId(Long spaceId, Long userId);

    void deleteBySpaceIdAndUserId(Long spaceId, Long userId);

    void deleteBySpaceId(Long spaceId);
}
