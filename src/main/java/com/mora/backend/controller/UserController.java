package com.mora.backend.controller;

import com.mora.backend.model.dto.request.UpdateProfileRequest;
import com.mora.backend.model.dto.request.ChangePasswordRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile() {
        UserResponse response = userService.getCurrentUserResponse();
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .message("Lấy thông tin cá nhân thành công")
                        .result(response)
                        .build()
        );
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = userService.updateProfile(request);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .message("Cập nhật thông tin cá nhân thành công")
                        .result(response)
                        .build()
        );
    }

    @PostMapping("/profile/avatar")
    public ResponseEntity<ApiResponse<UserResponse>> updateAvatar(@RequestParam("file") MultipartFile file) {
        UserResponse response = userService.updateAvatar(file);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .message("Cập nhật ảnh đại diện thành công")
                        .result(response)
                        .build()
        );
    }

    @PutMapping("/profile/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Thay đổi mật khẩu thành công")
                        .build()
        );
    }
}
