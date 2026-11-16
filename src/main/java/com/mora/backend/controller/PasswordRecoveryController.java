package com.mora.backend.controller;

import com.mora.backend.model.dto.request.*;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.service.PasswordRecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;

    @PostMapping("/forgot-password")
    public ApiResponse<Void> request(@Valid @RequestBody ForgotPasswordRequest body, HttpServletRequest request) {
        service.request(body.email(), request.getRemoteAddr());
        return ApiResponse.<Void>builder().message(
                "Nếu email này có tài khoản Mora, bạn sẽ nhận được liên kết khôi phục.").build();
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest body, HttpServletRequest request) {
        service.reset(body.token(), body.newPassword(), request.getRemoteAddr());
        return ApiResponse.<Void>builder().message("Đã đặt lại mật khẩu. Vui lòng đăng nhập.").build();
    }
}
