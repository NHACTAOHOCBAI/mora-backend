package com.mora.backend.model.dto.response;

import com.mora.backend.model.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private String avatarUrl;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
