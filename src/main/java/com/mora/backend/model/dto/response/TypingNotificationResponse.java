package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingNotificationResponse {
    private Long spaceId;
    private Long userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private boolean typing;
}
