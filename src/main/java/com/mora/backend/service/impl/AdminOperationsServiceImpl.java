package com.mora.backend.service.impl;

import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
import com.mora.backend.model.dto.response.AdminOperationsResponses;
import com.mora.backend.model.dto.response.PageResponse;
import com.mora.backend.model.entity.Document;
import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.model.entity.Space;
import com.mora.backend.model.entity.SpaceMember;
import com.mora.backend.model.entity.User;
import com.mora.backend.repository.DocumentRepository;
import com.mora.backend.repository.SpaceMemberRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.repository.UserRepository;
import com.mora.backend.service.AdminOperationsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOperationsServiceImpl implements AdminOperationsService {
    private final UserRepository users;
    private final SpaceRepository spaces;
    private final DocumentRepository documents;
    private final SpaceMemberRepository members;

    @Override
    public AdminOperationsResponses.Dashboard dashboard(int rangeDays) {
        int days = normalizeRange(rangeDays);
        LocalDate start = LocalDate.now().minusDays(days - 1);
        List<User> allUsers = users.findAll();
        List<Space> allSpaces = spaces.findAll();
        List<Document> allDocuments = documents.findAll();
        Map<DocumentStatus, Long> statuses = new EnumMap<>(DocumentStatus.class);
        for (DocumentStatus status : DocumentStatus.values()) statuses.put(status, 0L);
        allDocuments.forEach(document -> statuses.compute(document.getStatus(), (key, value) -> value + 1));

        List<AdminOperationsResponses.DailyActivity> activity = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = start.plusDays(offset);
            activity.add(AdminOperationsResponses.DailyActivity.builder().date(date)
                    .users(countCreated(allUsers, date)).spaces(countCreated(allSpaces, date))
                    .documents(countCreated(allDocuments, date)).build());
        }
        List<AdminOperationsResponses.DocumentSummary> failed = allDocuments.stream()
                .filter(document -> document.getStatus() == DocumentStatus.FAILED)
                .sorted(Comparator.comparing(Document::getFailedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5).map(this::documentSummary).toList();
        List<AdminOperationsResponses.SpaceSummary> recent = allSpaces.stream()
                .sorted(Comparator.comparing(Space::getLastActivityAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5).map(this::spaceSummary).toList();
        long attention = statuses.get(DocumentStatus.FAILED) + statuses.get(DocumentStatus.UPLOADING)
                + statuses.get(DocumentStatus.PARSING) + statuses.get(DocumentStatus.INDEXING);
        return AdminOperationsResponses.Dashboard.builder().rangeDays(days).totalUsers(allUsers.size())
                .totalSpaces(allSpaces.size()).totalDocuments(allDocuments.size()).documentsNeedingAttention(attention)
                .activity(activity).documentStatusCounts(statuses).failedDocuments(failed).recentSpaces(recent).build();
    }

    @Override
    public PageResponse<AdminOperationsResponses.SpaceSummary> spaces(int page, int limit, String search, String sortBy, String sortOrder) {
        Page<Space> result = spaces.searchForAdmin(search, pageable(page, limit, sortBy, sortOrder, "lastActivityAt", List.of("id", "name", "createdAt", "updatedAt", "lastActivityAt")));
        return page(result.map(this::spaceSummary), page, limit);
    }

    @Override
    public AdminOperationsResponses.SpaceDetail spaceDetail(Long id) {
        Space space = spaces.findById(id).orElseThrow(() -> new AppException(ErrorCode.SPACE_NOT_FOUND));
        List<AdminOperationsResponses.MemberSummary> memberList = members.findBySpaceId(id).stream().map(member ->
                AdminOperationsResponses.MemberSummary.builder().userId(member.getUser().getId()).fullName(member.getUser().getFullName())
                        .email(member.getUser().getEmail()).avatarUrl(member.getUser().getAvatarUrl()).role(member.getRole()).joinedAt(member.getJoinedAt()).build()).toList();
        return AdminOperationsResponses.SpaceDetail.builder().space(spaceSummary(space)).description(space.getDescription())
                .members(memberList).documents(documents.findBySpaceId(id).stream().map(this::documentSummary).toList()).build();
    }

    @Override
    public PageResponse<AdminOperationsResponses.DocumentSummary> documents(int page, int limit, String search, String sortBy, String sortOrder, DocumentStatus status, Long spaceId, Long uploaderId) {
        Page<Document> result = documents.searchForAdmin(search, status, spaceId, uploaderId,
                pageable(page, limit, sortBy, sortOrder, "createdAt", List.of("id", "name", "createdAt", "updatedAt", "status", "fileSize")));
        return page(result.map(this::documentSummary), page, limit);
    }

    @Override
    public AdminOperationsResponses.DocumentSummary documentDetail(Long id) {
        return documentSummary(documents.findById(id).orElseThrow(() -> new AppException(ErrorCode.DOCUMENT_NOT_FOUND)));
    }

    @Override
    public AdminOperationsResponses.UserDetail userDetail(Long id) {
        User user = users.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        List<AdminOperationsResponses.SpaceSummary> owned = spaces.findByUser(user).stream().map(this::spaceSummary).toList();
        List<AdminOperationsResponses.SpaceSummary> joined = members.findByUserId(id).stream()
                .map(SpaceMember::getSpace).filter(space -> !space.getUser().getId().equals(id)).map(this::spaceSummary).toList();
        return AdminOperationsResponses.UserDetail.builder().id(user.getId()).email(user.getEmail()).fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl()).role(user.getRole()).active(user.isActive()).createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt())
                .ownedSpaces(owned).memberSpaces(joined).uploadedDocuments(documents.findByUploadedById(id).stream().map(this::documentSummary).toList()).build();
    }

    private AdminOperationsResponses.SpaceSummary spaceSummary(Space space) {
        return AdminOperationsResponses.SpaceSummary.builder().id(space.getId()).name(space.getName())
                .ownerName(space.getUser() == null ? "—" : space.getUser().displayName()).memberCount(members.findBySpaceId(space.getId()).size())
                .documentCount(documents.findBySpaceId(space.getId()).size()).createdAt(space.getCreatedAt()).lastActivityAt(space.getLastActivityAt()).build();
    }

    private AdminOperationsResponses.DocumentSummary documentSummary(Document document) {
        return AdminOperationsResponses.DocumentSummary.builder().id(document.getId()).name(document.getName()).status(document.getStatus())
                .spaceId(document.getSpace().getId()).spaceName(document.getSpace().getName())
                .uploadedById(document.getUploadedBy() == null ? null : document.getUploadedBy().getId())
                .uploadedByName(document.getUploadedBy() == null ? "—" : document.getUploadedBy().displayName())
                .fileSize(document.getFileSize()).contentType(document.getContentType()).createdAt(document.getCreatedAt()).updatedAt(document.getUpdatedAt())
                .failureCode(document.getFailureCode()).failureMessage(document.getFailureMessage()).failedAt(document.getFailedAt()).build();
    }

    private Pageable pageable(int page, int limit, String sortBy, String sortOrder, String fallback, List<String> allowed) {
        String field = allowed.contains(sortBy) ? sortBy : fallback;
        Sort.Direction direction = "ASC".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(Math.max(0, page - 1), Math.min(Math.max(1, limit), 100), Sort.by(direction, field));
    }

    private <T> PageResponse<T> page(Page<T> source, int page, int limit) {
        return new PageResponse<>(source.getContent(), new PageResponse.Meta(new PageResponse.Pagination(source.getTotalElements(), page, limit)));
    }
    private int normalizeRange(int rangeDays) { return rangeDays == 7 || rangeDays == 90 ? rangeDays : 30; }
    private long countCreated(List<? extends Object> records, LocalDate date) {
        return records.stream().filter(record -> {
            LocalDateTime created = record instanceof User user ? user.getCreatedAt() : record instanceof Space space ? space.getCreatedAt() : ((Document) record).getCreatedAt();
            return created != null && created.toLocalDate().equals(date);
        }).count();
    }
}
