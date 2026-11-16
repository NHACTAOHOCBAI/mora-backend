package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.*;
public record ForgotPasswordRequest(
        @NotBlank(message = "Vui lòng nhập email")
        @Email(message = "Email không hợp lệ")
        @Size(max = 100, message = "Email tối đa 100 ký tự") String email
) {}
