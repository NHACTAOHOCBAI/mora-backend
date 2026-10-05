package com.mora.backend.model.dto.request;

import com.mora.backend.model.entity.SpaceRole;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceInvitationCreateRequest {

    @NotNull(message = "Vai trò không được để trống")
    @Builder.Default
    private SpaceRole role = SpaceRole.EDITOR;

    private Integer durationDays; // null hoặc <= 0 nghĩa là vĩnh viễn (hoặc mặc định 7 ngày)

    private String inviteEmail;
}
