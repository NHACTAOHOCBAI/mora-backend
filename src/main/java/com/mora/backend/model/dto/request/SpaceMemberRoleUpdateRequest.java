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
public class SpaceMemberRoleUpdateRequest {

    @NotNull(message = "Vai trò không được để trống")
    private SpaceRole role;
}
