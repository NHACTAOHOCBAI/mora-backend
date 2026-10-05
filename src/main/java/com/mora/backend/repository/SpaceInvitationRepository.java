package com.mora.backend.repository;

import com.mora.backend.model.entity.InvitationStatus;
import com.mora.backend.model.entity.SpaceInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceInvitationRepository extends JpaRepository<SpaceInvitation, Long> {

    Optional<SpaceInvitation> findByInviteCode(String inviteCode);

    List<SpaceInvitation> findBySpaceId(Long spaceId);

    List<SpaceInvitation> findBySpaceIdAndStatus(Long spaceId, InvitationStatus status);

    void deleteBySpaceId(Long spaceId);
}
