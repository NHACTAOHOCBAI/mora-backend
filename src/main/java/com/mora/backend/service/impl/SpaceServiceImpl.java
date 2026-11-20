package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.request.SpaceInvitationCreateRequest;
import com.mora.backend.model.dto.request.SpaceMemberAddRequest;
import com.mora.backend.model.dto.request.SpaceMemberRoleUpdateRequest;
import com.mora.backend.model.dto.request.SpaceUpdateRequest;
import com.mora.backend.model.dto.request.SpaceLeaveRequest;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.DocumentService;
import com.mora.backend.service.SpaceService;
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpaceServiceImpl implements SpaceService {

    private final SpaceRepository spaceRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceInvitationRepository spaceInvitationRepository;
    private final SpaceMemberInvitationRepository spaceMemberInvitationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final org.springframework.beans.factory.ObjectProvider<com.mora.backend.service.BillingService> billingService;

    @Override
    @Transactional
    public SpaceResponse createSpace(SpaceCreateRequest request) {
        User currentUser = userService.getCurrentUser();
        var billing = billingService.getIfAvailable();
        if (billing != null) billing.requireCanCreateSpace(currentUser);
        Space space = Space.builder()
                .name(request.getName())
                .description(request.getDescription())
                .user(currentUser)
                .lastActivityAt(LocalDateTime.now())
                .build();
        space = spaceRepository.save(space);

        // Add creator as OWNER in SpaceMember
        SpaceMember ownerMember = SpaceMember.builder()
                .space(space)
                .user(currentUser)
                .role(SpaceRole.OWNER)
                .build();
        spaceMemberRepository.save(ownerMember);

        log.info("Space created successfully with ID {} by user {}", space.getId(), currentUser.getEmail());
        return convertToSpaceResponse(space, SpaceRole.OWNER, 1, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceResponse> getAllSpaces() {
        User currentUser = userService.getCurrentUser();
        List<Space> spaces;
        if (currentUser.getRole() == Role.ROLE_ADMIN) {
            spaces = spaceRepository.findAll();
        } else {
            spaces = spaceRepository.findAllAccessibleSpaces(currentUser);
        }

        return spaces.stream()
                .map(space -> {
                    SpaceRole role = getUserRoleInSpace(space.getId(), currentUser);
                    int memberCount = spaceMemberRepository.findBySpaceId(space.getId()).size();
                    int docCount = documentRepository.findBySpaceId(space.getId()).size();
                    return convertToSpaceResponse(space, role, memberCount, docCount);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        User currentUser = userService.getCurrentUser();
        List<Space> spaces = currentUser.getRole() == Role.ROLE_ADMIN
                ? spaceRepository.findAll()
                : spaceRepository.findAllAccessibleSpaces(currentUser);

        List<SpaceResponse> recentSpaces = spaces.stream()
                .sorted(Comparator.comparing(
                        (Space space) -> space.getLastActivityAt() != null ? space.getLastActivityAt() : space.getUpdatedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(space -> convertToSpaceResponse(space, getUserRoleInSpace(space.getId(), currentUser),
                        spaceMemberRepository.findBySpaceId(space.getId()).size(), documentRepository.findBySpaceId(space.getId()).size()))
                .toList();

        List<DashboardResponse.ProcessingDocument> processingDocuments = spaces.stream()
                .flatMap(space -> documentRepository.findBySpaceId(space.getId()).stream())
                .filter(document -> document.getStatus() == DocumentStatus.UPLOADING
                        || document.getStatus() == DocumentStatus.PARSING
                        || document.getStatus() == DocumentStatus.INDEXING)
                .sorted(Comparator.comparing(Document::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(document -> DashboardResponse.ProcessingDocument.builder()
                        .id(document.getId()).name(document.getName()).status(document.getStatus())
                        .spaceId(document.getSpace().getId()).spaceName(document.getSpace().getName())
                        .updatedAt(document.getUpdatedAt()).build())
                .toList();
        List<SpaceMemberInvitationResponse> pending = spaceMemberInvitationRepository
                .findByInviteeIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), InvitationStatus.PENDING).stream()
                .map(this::convertToMemberInvitationResponse).toList();

        return DashboardResponse.builder()
                .recentSpaces(recentSpaces)
                .processingDocuments(processingDocuments)
                .pendingMemberInvitations(pending.stream().limit(5).toList())
                .pendingMemberInvitationCount(pending.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceDetailResponse getSpaceById(Long id) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found", id);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        SpaceRole userRole = getUserRoleInSpace(id, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<Document> documents = documentRepository.findBySpaceId(id);
        List<DocumentResponse> docResponses = documents.stream()
                .map(d -> DocumentResponse.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .storageUrl(d.getStorageUrl())
                        .fileSize(d.getFileSize())
                        .contentType(d.getContentType())
                        .spaceId(d.getSpace().getId())
                        .status(d.getStatus())
                        .uploadedById(d.getUploadedBy() != null ? d.getUploadedBy().getId() : null)
                        .uploadedByName(d.getUploadedBy() != null ? (d.getUploadedBy().displayName()) : null)
                        .createdAt(d.getCreatedAt())
                        .build())
                .toList();

        List<SpaceMember> members = spaceMemberRepository.findBySpaceId(id);
        List<SpaceMemberResponse> memberResponses = members.stream()
                .map(this::convertToMemberResponse)
                .toList();

        String ownerName = space.getUser() != null
                ? (space.getUser().displayName())
                : "System";

        return SpaceDetailResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .createdAt(space.getCreatedAt())
                .updatedAt(space.getUpdatedAt())
                .currentUserRole(userRole)
                .memberCount(members.size())
                .documents(docResponses)
                .members(memberResponses)
                .ownerName(ownerName)
                .build();
    }

    @Override
    @Transactional
    public SpaceResponse updateSpace(Long id, SpaceUpdateRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
        SpaceRole userRole = getUserRoleInSpace(id, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }
        space.setName(request.getName().trim());
        space.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        Space updated = spaceRepository.save(space);
        return convertToSpaceResponse(updated, getUserRoleInSpace(id, currentUser),
                spaceMemberRepository.findBySpaceId(id).size(), documentRepository.findBySpaceId(id).size());
    }

    @Override
    @Transactional
    public void deleteSpace(Long id) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("Space with ID {} not found for deletion", id);
                    return new AppException(ErrorCode.SPACE_NOT_FOUND);
                });

        SpaceRole userRole = getUserRoleInSpace(id, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        // 1. Delete invitations
        spaceInvitationRepository.deleteBySpaceId(id);
        spaceMemberInvitationRepository.deleteBySpaceId(id);

        // 2. Delete space members
        spaceMemberRepository.deleteBySpaceId(id);

        // 3. Delete space-level chat messages
        chatMessageRepository.deleteBySpaceId(id);

        // 4. Delete all documents in this space
        List<Document> documents = documentRepository.findBySpaceId(id);
        for (Document doc : documents) {
            documentService.deleteDocument(doc.getId());
        }

        // 5. Delete the space itself
        spaceRepository.delete(space);
        log.info("Space {} successfully deleted by user {}", id, currentUser.getEmail());
    }

    @Override
    @Transactional
    public void leaveSpace(Long id, SpaceLeaveRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceMember currentMember = spaceMemberRepository.findBySpaceIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_MEMBER_NOT_FOUND));
        boolean isDirectOwner = space.getUser() != null && space.getUser().getId().equals(currentUser.getId());

        if (isDirectOwner) {
            Long successorId = request.getSuccessorUserId();
            if (successorId == null || successorId.equals(currentUser.getId())) {
                throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
            }
            SpaceMember successor = spaceMemberRepository.findBySpaceIdAndUserId(id, successorId)
                    .orElseThrow(() -> new AppException(ErrorCode.SPACE_MEMBER_NOT_FOUND));
            successor.setRole(SpaceRole.OWNER);
            spaceMemberRepository.save(successor);
            space.setUser(successor.getUser());
            spaceRepository.save(space);
        }

        spaceMemberRepository.delete(currentMember);
        log.info("User {} left space {}", currentUser.getId(), id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceMemberResponse> getSpaceMembers(Long spaceId) {
        User currentUser = userService.getCurrentUser();
        SpaceRole userRole = getUserRoleInSpace(spaceId, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<SpaceMember> members = spaceMemberRepository.findBySpaceId(spaceId);
        return members.stream().map(this::convertToMemberResponse).toList();
    }

    @Override
    @Transactional
    public SpaceMemberInvitationResponse inviteSpaceMember(Long spaceId, SpaceMemberAddRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole userRole = getUserRoleInSpace(spaceId, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && userRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        String search = request.getEmail().trim();
        User targetUser = userRepository.findByEmail(search)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (spaceMemberRepository.existsBySpaceIdAndUserId(spaceId, targetUser.getId())) {
            throw new AppException(ErrorCode.SPACE_MEMBER_ALREADY_EXISTS);
        }
        if (spaceMemberInvitationRepository.existsBySpaceIdAndInviteeIdAndStatus(spaceId, targetUser.getId(), InvitationStatus.PENDING)) {
            throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        }

        SpaceMemberInvitation invitation = SpaceMemberInvitation.builder()
                .space(space).inviter(currentUser).invitee(targetUser)
                .role(request.getRole() != null ? request.getRole() : SpaceRole.EDITOR)
                .build();
        invitation = spaceMemberInvitationRepository.save(invitation);
        return convertToMemberInvitationResponse(invitation);
    }

    @Override @Transactional(readOnly = true)
    public List<SpaceMemberInvitationResponse> getMemberInvitations(Long spaceId) {
        User user = userService.getCurrentUser();
        if (user.getRole() != Role.ROLE_ADMIN && getUserRoleInSpace(spaceId, user) != SpaceRole.OWNER) throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        return spaceMemberInvitationRepository.findBySpaceIdOrderByCreatedAtDesc(spaceId).stream().map(this::convertToMemberInvitationResponse).toList();
    }

    @Override @Transactional
    public SpaceMemberInvitationResponse acceptMemberInvitation(Long invitationId) {
        SpaceMemberInvitation invitation = requireInviteeInvitation(invitationId);
        if (invitation.getStatus() != InvitationStatus.PENDING) throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        if (!spaceMemberRepository.existsBySpaceIdAndUserId(invitation.getSpace().getId(), invitation.getInvitee().getId())) {
            spaceMemberRepository.save(SpaceMember.builder().space(invitation.getSpace()).user(invitation.getInvitee()).role(invitation.getRole()).build());
        }
        invitation.setStatus(InvitationStatus.ACCEPTED);
        return convertToMemberInvitationResponse(spaceMemberInvitationRepository.save(invitation));
    }

    @Override @Transactional
    public SpaceMemberInvitationResponse declineMemberInvitation(Long invitationId) {
        SpaceMemberInvitation invitation = requireInviteeInvitation(invitationId);
        if (invitation.getStatus() != InvitationStatus.PENDING) throw new AppException(ErrorCode.INVALID_REQUEST_PARAMETER);
        invitation.setStatus(InvitationStatus.DECLINED);
        return convertToMemberInvitationResponse(spaceMemberInvitationRepository.save(invitation));
    }

    @Override @Transactional
    public void revokeMemberInvitation(Long spaceId, Long invitationId) {
        User user = userService.getCurrentUser();
        if (user.getRole() != Role.ROLE_ADMIN && getUserRoleInSpace(spaceId, user) != SpaceRole.OWNER) throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        SpaceMemberInvitation invitation = spaceMemberInvitationRepository.findById(invitationId).orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.getSpace().getId().equals(spaceId)) throw new AppException(ErrorCode.INVITATION_NOT_FOUND);
        invitation.setStatus(InvitationStatus.REVOKED); spaceMemberInvitationRepository.save(invitation);
    }

    @Override
    @Transactional
    public SpaceMemberResponse updateMemberRole(Long spaceId, Long userId, SpaceMemberRoleUpdateRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole callerRole = getUserRoleInSpace(spaceId, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && callerRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        if (space.getUser() != null && space.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_REMOVE_OWNER);
        }

        SpaceMember member = spaceMemberRepository.findBySpaceIdAndUserId(spaceId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        member.setRole(request.getRole());
        member = spaceMemberRepository.save(member);
        log.info("Updated user {} in space {} to role {}", userId, spaceId, member.getRole());
        return convertToMemberResponse(member);
    }

    @Override
    @Transactional
    public void removeSpaceMember(Long spaceId, Long userId) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole callerRole = getUserRoleInSpace(spaceId, currentUser);

        // A user can leave the space themselves, or OWNER/ADMIN can remove them
        boolean isSelfLeaving = currentUser.getId().equals(userId);
        if (!isSelfLeaving && currentUser.getRole() != Role.ROLE_ADMIN && callerRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        if (space.getUser() != null && space.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.CANNOT_REMOVE_OWNER);
        }

        if (!spaceMemberRepository.existsBySpaceIdAndUserId(spaceId, userId)) {
            throw new AppException(ErrorCode.SPACE_MEMBER_NOT_FOUND);
        }

        spaceMemberRepository.deleteBySpaceIdAndUserId(spaceId, userId);
        log.info("Removed user {} from space {}", userId, spaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceRole getUserRoleInSpace(Long spaceId, User user) {
        if (user == null) return null;
        if (user.getRole() == Role.ROLE_ADMIN) return SpaceRole.OWNER;

        Optional<SpaceMember> memberOpt = spaceMemberRepository.findBySpaceIdAndUserId(spaceId, user.getId());
        if (memberOpt.isPresent()) {
            return memberOpt.get().getRole();
        }

        // Fallback: check if user is the direct owner on the Space record
        Optional<Space> spaceOpt = spaceRepository.findById(spaceId);
        if (spaceOpt.isPresent() && spaceOpt.get().getUser() != null && spaceOpt.get().getUser().getId().equals(user.getId())) {
            return SpaceRole.OWNER;
        }

        return null;
    }

    @Override
    @Transactional
    public SpaceInvitationResponse createInvitation(Long spaceId, SpaceInvitationCreateRequest request) {
        User currentUser = userService.getCurrentUser();
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));

        SpaceRole callerRole = getUserRoleInSpace(spaceId, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && callerRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        String inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        LocalDateTime expiresAt = null;
        if (request.getDurationDays() != null && request.getDurationDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getDurationDays());
        }

        SpaceInvitation invitation = SpaceInvitation.builder()
                .space(space)
                .inviter(currentUser)
                .inviteCode(inviteCode)
                .role(request.getRole() != null ? request.getRole() : SpaceRole.EDITOR)
                .status(InvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .build();
        invitation = spaceInvitationRepository.save(invitation);

        log.info("Created invitation with code {} for space {}", inviteCode, spaceId);
        return convertToInvitationResponse(invitation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpaceInvitationResponse> getSpaceInvitations(Long spaceId) {
        User currentUser = userService.getCurrentUser();
        SpaceRole callerRole = getUserRoleInSpace(spaceId, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && callerRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        List<SpaceInvitation> invitations = spaceInvitationRepository.findBySpaceId(spaceId);
        return invitations.stream().map(this::convertToInvitationResponse).toList();
    }

    @Override
    @Transactional
    public void revokeInvitation(Long spaceId, Long invitationId) {
        User currentUser = userService.getCurrentUser();
        spaceRepository.findForUpdate(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
        SpaceRole callerRole = getUserRoleInSpace(spaceId, currentUser);
        if (currentUser.getRole() != Role.ROLE_ADMIN && callerRole != SpaceRole.OWNER) {
            throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        SpaceInvitation invitation = spaceInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));

        if (!invitation.getSpace().getId().equals(spaceId)) {
            throw new AppException(ErrorCode.INVITATION_NOT_FOUND);
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        spaceInvitationRepository.save(invitation);
        log.info("Revoked invitation {} for space {}", invitationId, spaceId);
    }

    @Override
    @Transactional(readOnly = true)
    public SpaceJoinPreviewResponse previewInvitation(String inviteCode) {
        User currentUser = userService.getCurrentUser();
        SpaceInvitation invitation = spaceInvitationRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_FOUND);
        }

        if (invitation.getExpiresAt() != null && !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVITATION_EXPIRED);
        }

        Space space = invitation.getSpace();
        int memberCount = spaceMemberRepository.findBySpaceId(space.getId()).size();
        int docCount = documentRepository.findBySpaceId(space.getId()).size();
        boolean isAlreadyMember = (space.getUser() != null && space.getUser().getId().equals(currentUser.getId()))
                || spaceMemberRepository.existsBySpaceIdAndUserId(space.getId(), currentUser.getId());

        String inviterName = invitation.getInviter() != null
                ? (invitation.getInviter().displayName())
                : "Thành viên nhóm";

        return SpaceJoinPreviewResponse.builder()
                .spaceId(space.getId())
                .spaceName(space.getName())
                .spaceDescription(space.getDescription())
                .inviterName(inviterName)
                .inviterAvatar(invitation.getInviter() != null ? invitation.getInviter().getAvatarUrl() : null)
                .role(invitation.getRole())
                .memberCount(memberCount)
                .documentCount(docCount)
                .isAlreadyMember(isAlreadyMember)
                .build();
    }

    @Override
    @Transactional
    public SpaceResponse joinSpaceByInviteCode(String inviteCode) {
        User currentUser = userService.getCurrentUser();
        String code = inviteCode.trim().toUpperCase(java.util.Locale.ROOT);
        Long spaceId = spaceInvitationRepository.findSpaceIdByInviteCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        Space space = spaceRepository.findForUpdate(spaceId)
                .orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
        // Read the invitation only after acquiring the workspace lock, so a concurrent
        // revoke or join cannot leave a stale invitation or create duplicate membership.
        SpaceInvitation invitation = spaceInvitationRepository.findByInviteCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new AppException(ErrorCode.INVITATION_NOT_FOUND);
        }

        if (invitation.getExpiresAt() != null && !invitation.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.INVITATION_EXPIRED);
        }

        if (space.getUser() != null && space.getUser().getId().equals(currentUser.getId())) {
            return convertToSpaceResponse(space, SpaceRole.OWNER,
                    spaceMemberRepository.findBySpaceId(spaceId).size(), documentRepository.findBySpaceId(spaceId).size());
        }

        // Check if already a member
        Optional<SpaceMember> existingMemberOpt = spaceMemberRepository.findBySpaceIdAndUserId(space.getId(), currentUser.getId());
        if (existingMemberOpt.isPresent()) {
            int memberCount = spaceMemberRepository.findBySpaceId(space.getId()).size();
            int docCount = documentRepository.findBySpaceId(space.getId()).size();
            return convertToSpaceResponse(space, existingMemberOpt.get().getRole(), memberCount, docCount);
        }

        SpaceMember newMember = SpaceMember.builder()
                .space(space)
                .user(currentUser)
                .role(invitation.getRole())
                .build();
        spaceMemberRepository.save(newMember);

        int memberCount = spaceMemberRepository.findBySpaceId(space.getId()).size();
        int docCount = documentRepository.findBySpaceId(space.getId()).size();

        log.info("User {} joined space {} as {}", currentUser.getId(), space.getId(), invitation.getRole());
        return convertToSpaceResponse(space, invitation.getRole(), memberCount, docCount);
    }

    private SpaceResponse convertToSpaceResponse(Space space, SpaceRole role, int memberCount, int documentCount) {
        String ownerName = space.getUser() != null
                ? (space.getUser().displayName())
                : "System";

        return SpaceResponse.builder()
                .id(space.getId())
                .name(space.getName())
                .description(space.getDescription())
                .createdAt(space.getCreatedAt())
                .updatedAt(space.getUpdatedAt())
                .lastActivityAt(space.getLastActivityAt())
                .currentUserRole(role)
                .memberCount(memberCount)
                .documentCount(documentCount)
                .ownerName(ownerName)
                .build();
    }

    private SpaceMemberResponse convertToMemberResponse(SpaceMember member) {
        User u = member.getUser();
        return SpaceMemberResponse.builder()
                .id(member.getId())
                .userId(u.getId())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private SpaceInvitationResponse convertToInvitationResponse(SpaceInvitation inv) {
        String inviterName = inv.getInviter() != null
                ? (inv.getInviter().displayName())
                : "System";

        return SpaceInvitationResponse.builder()
                .id(inv.getId())
                .spaceId(inv.getSpace().getId())
                .spaceName(inv.getSpace().getName())
                .inviteCode(inv.getInviteCode())
                .role(inv.getRole())
                .status(inv.getStatus())
                .expiresAt(inv.getExpiresAt())
                .createdAt(inv.getCreatedAt())
                .inviterName(inviterName)
                .build();
    }

    private SpaceMemberInvitation requireInviteeInvitation(Long id) {
        User currentUser = userService.getCurrentUser();
        SpaceMemberInvitation invitation = spaceMemberInvitationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INVITATION_NOT_FOUND));
        if (!invitation.getInvitee().getId().equals(currentUser.getId())) throw new AppException(ErrorCode.SPACE_ACCESS_DENIED);
        return invitation;
    }

    private SpaceMemberInvitationResponse convertToMemberInvitationResponse(SpaceMemberInvitation invitation) {
        return SpaceMemberInvitationResponse.builder().id(invitation.getId())
                .spaceId(invitation.getSpace().getId()).spaceName(invitation.getSpace().getName())
                .inviterName(invitation.getInviter().displayName()).inviteeEmail(invitation.getInvitee().getEmail())
                .role(invitation.getRole()).status(invitation.getStatus()).createdAt(invitation.getCreatedAt()).build();
    }
}
