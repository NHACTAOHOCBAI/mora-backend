package com.mora.backend.controller;

import com.mora.backend.model.dto.request.AdminUserUpdateRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.service.UserService;
import com.mora.backend.service.BillingService;
import com.mora.backend.repository.UserRepository;
import com.mora.backend.model.dto.request.PlanChangeRequest;
import com.mora.backend.model.dto.response.EntitlementResponse;
import com.mora.backend.exception.AppException;
import com.mora.backend.exception.ErrorCode;
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
    private final BillingService billingService;
    private final UserRepository userRepository;

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

    @PatchMapping("/{id}/plan")
    public ResponseEntity<ApiResponse<EntitlementResponse>> updatePlan(@PathVariable Long id, @Valid @RequestBody PlanChangeRequest request) {
        var user = userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.<EntitlementResponse>builder().message("Đã cập nhật gói người dùng").result(billingService.changePlan(user, request, "admin")).build());
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

    @PostMapping("/{id}/impersonate")
    public ResponseEntity<ApiResponse<com.mora.backend.model.dto.response.AuthResponse>> impersonateUser(@PathVariable Long id) {
        com.mora.backend.model.dto.response.AuthResponse response = userService.impersonateUser(id);
        return ResponseEntity.ok(
                ApiResponse.<com.mora.backend.model.dto.response.AuthResponse>builder()
                        .message("Đăng nhập bằng quyền Admin thành công")
                        .result(response)
                        .build()
        );
    }
}
