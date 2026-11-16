package com.mora.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email không được để trống")
    @Email
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
