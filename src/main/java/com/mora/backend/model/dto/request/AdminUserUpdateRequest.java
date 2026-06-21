package com.mora.backend.model.dto.request;

import com.mora.backend.model.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {

    @NotNull(message = "Trạng thái active không được để trống")
    private Boolean active;

    @NotNull(message = "Role không được để trống")
    private Role role;
}
