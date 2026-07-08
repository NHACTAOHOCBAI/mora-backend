package com.mora.backend.controller;

import com.mora.backend.model.dto.request.AdminUserUpdateRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mora.backend.model.dto.response.PageResponse;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortOrder
    ) {
        PageResponse<UserResponse> response = userService.getAllUsers(page, limit, search, sortBy, sortOrder);
        return ResponseEntity.ok(
                ApiResponse.<PageResponse<UserResponse>>builder()
                        .message("Lấy danh sách người dùng thành công")
                        .result(response)
                        .build()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        UserResponse response = userService.updateUserByAdmin(id, request);
        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .message("Cập nhật thông tin người dùng thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa người dùng thành công")
                        .build()
        );
    }
}
