package com.mora.backend.model.dto.response;

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
public class SpaceMemberResponse {
    private Long id;
    private Long userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private SpaceRole role;
    private LocalDateTime joinedAt;
}
