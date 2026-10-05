package com.mora.backend.model.dto.request;

import com.mora.backend.model.entity.SpaceRole;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceMemberAddRequest {

    @NotBlank(message = "Tên đăng nhập hoặc Email không được để trống")
    private String usernameOrEmail;

    @Builder.Default
    private SpaceRole role = SpaceRole.EDITOR;
}
