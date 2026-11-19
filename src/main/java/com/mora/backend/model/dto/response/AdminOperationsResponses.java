package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.DocumentStatus;
import com.mora.backend.model.entity.Role;
import com.mora.backend.model.entity.SpaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public final class AdminOperationsResponses {
    private AdminOperationsResponses() {}

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Dashboard {
        private int rangeDays;
        private long totalUsers;
        private long totalSpaces;
        private long totalDocuments;
        private long documentsNeedingAttention;
        private List<DailyActivity> activity;
        private Map<DocumentStatus, Long> documentStatusCounts;
        private List<DocumentSummary> failedDocuments;
        private List<SpaceSummary> recentSpaces;
        private AiUsageSummary aiUsage;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyActivity {
        private LocalDate date;
        private long users;
        private long spaces;
        private long documents;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AiUsageSummary {
        private long totalTokens;
        private long uncachedInputTokens;
        private long cachedInputTokens;
        private long outputTokens;
        private BigDecimal totalCostVnd;
        private long requestCount;
        private LocalDateTime trackingStartedAt;
        private List<DailyAiUsage> daily;
        private List<AiUsageBreakdown> byOperation;
        private List<AiUsageBreakdown> byModel;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DailyAiUsage {
        private LocalDate date; private long totalTokens; private BigDecimal costVnd; private long requestCount;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AiUsageBreakdown {
        private String key; private long totalTokens; private BigDecimal costVnd; private long requestCount;
        private long uncachedInputTokens;
        private long cachedInputTokens;
        private long outputTokens;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SpaceSummary {
        private Long id; private String name; private String ownerName; private int memberCount; private int documentCount;
        private LocalDateTime createdAt; private LocalDateTime lastActivityAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DocumentSummary {
        private Long id; private String name; private DocumentStatus status; private Long spaceId; private String spaceName;
        private Long uploadedById; private String uploadedByName; private Long fileSize; private String contentType;
        private LocalDateTime createdAt; private LocalDateTime updatedAt; private String failureCode; private String failureMessage; private LocalDateTime failedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SpaceDetail {
        private SpaceSummary space;
        private String description;
        private List<MemberSummary> members;
        private List<DocumentSummary> documents;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MemberSummary {
        private Long userId; private String fullName; private String email; private String avatarUrl; private SpaceRole role; private LocalDateTime joinedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UserDetail {
        private Long id; private String email; private String fullName; private String avatarUrl; private Role role; private boolean active;
        private LocalDateTime createdAt; private LocalDateTime updatedAt; private List<SpaceSummary> ownedSpaces;
        private List<SpaceSummary> memberSpaces; private List<DocumentSummary> uploadedDocuments;
    }
}
