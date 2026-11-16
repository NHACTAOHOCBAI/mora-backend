package com.mora.backend.model.dto.request;

import com.mora.backend.model.entity.SpaceRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpaceMemberAddRequest {

    @NotBlank(message = "Email không được để trống")
    @Email
    private String email;

    @Builder.Default
    private SpaceRole role = SpaceRole.EDITOR;
}
