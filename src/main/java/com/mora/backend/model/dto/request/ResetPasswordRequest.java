package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.*;
public record ResetPasswordRequest(
        @NotBlank @Size(max = 128) String token,
        @NotBlank(message = "Vui lòng nhập mật khẩu mới")
        @Size(min = 6, max = 72, message = "Mật khẩu phải có từ 6 đến 72 ký tự") String newPassword
) {}
