package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;
}
