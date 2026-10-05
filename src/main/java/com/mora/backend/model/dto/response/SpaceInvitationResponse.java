package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.InvitationStatus;
import com.mora.backend.model.entity.SpaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceInvitationResponse {
    private Long id;
    private Long spaceId;
    private String spaceName;
    private String inviteCode;
    private String inviteEmail;
    private SpaceRole role;
    private InvitationStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String inviterName;
}
