package com.mora.backend.repository;

import com.mora.backend.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface SpaceMemberInvitationRepository extends JpaRepository<SpaceMemberInvitation, Long> {
    List<SpaceMemberInvitation> findBySpaceIdOrderByCreatedAtDesc(Long spaceId);
    List<SpaceMemberInvitation> findByInviteeIdAndStatusOrderByCreatedAtDesc(Long userId, InvitationStatus status);
    boolean existsBySpaceIdAndInviteeIdAndStatus(Long spaceId, Long inviteeId, InvitationStatus status);
    void deleteBySpaceId(Long spaceId);
}
