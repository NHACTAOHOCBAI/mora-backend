package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.SpaceRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceJoinPreviewResponse {
    private Long spaceId;
    private String spaceName;
    private String spaceDescription;
    private String inviterName;
    private String inviterAvatar;
    private SpaceRole role;
    private int memberCount;
    private int documentCount;
    private boolean isAlreadyMember;
}
