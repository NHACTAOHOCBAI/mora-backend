package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private List<SpaceResponse> recentSpaces;
    private List<ProcessingDocument> processingDocuments;
    private List<SpaceMemberInvitationResponse> pendingMemberInvitations;
    private int pendingMemberInvitationCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProcessingDocument {
        private Long id;
        private String name;
        private DocumentStatus status;
        private Long spaceId;
        private String spaceName;
        private LocalDateTime updatedAt;
    }
}
