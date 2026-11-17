package com.mora.backend.service;

import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.request.SpaceInvitationCreateRequest;
import com.mora.backend.model.dto.request.SpaceMemberAddRequest;
import com.mora.backend.model.dto.request.SpaceMemberRoleUpdateRequest;
import com.mora.backend.model.dto.request.SpaceUpdateRequest;
import com.mora.backend.model.dto.request.SpaceLeaveRequest;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.model.entity.SpaceRole;
import com.mora.backend.model.entity.User;

import java.util.List;

public interface SpaceService {
    SpaceResponse createSpace(SpaceCreateRequest request);
    List<SpaceResponse> getAllSpaces();
    DashboardResponse getDashboard();
    SpaceDetailResponse getSpaceById(Long id);
    SpaceResponse updateSpace(Long id, SpaceUpdateRequest request);
    void deleteSpace(Long id);
    void leaveSpace(Long id, SpaceLeaveRequest request);

    // RBAC & Member Management
    List<SpaceMemberResponse> getSpaceMembers(Long spaceId);
    SpaceMemberInvitationResponse inviteSpaceMember(Long spaceId, SpaceMemberAddRequest request);
    List<SpaceMemberInvitationResponse> getMemberInvitations(Long spaceId);
    SpaceMemberInvitationResponse acceptMemberInvitation(Long invitationId);
    SpaceMemberInvitationResponse declineMemberInvitation(Long invitationId);
    void revokeMemberInvitation(Long spaceId, Long invitationId);
    SpaceMemberResponse updateMemberRole(Long spaceId, Long userId, SpaceMemberRoleUpdateRequest request);
    void removeSpaceMember(Long spaceId, Long userId);
    SpaceRole getUserRoleInSpace(Long spaceId, User user);

    // Invitations
    SpaceInvitationResponse createInvitation(Long spaceId, SpaceInvitationCreateRequest request);
    List<SpaceInvitationResponse> getSpaceInvitations(Long spaceId);
    void revokeInvitation(Long spaceId, Long invitationId);
    SpaceJoinPreviewResponse previewInvitation(String inviteCode);
    SpaceResponse joinSpaceByInviteCode(String inviteCode);
}
