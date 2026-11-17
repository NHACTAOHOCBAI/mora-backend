package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.*;
import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SpaceMemberInvitationResponse {
    private Long id; private Long spaceId; private String spaceName; private String inviterName;
    private String inviteeEmail; private SpaceRole role; private InvitationStatus status; private LocalDateTime createdAt;
}
