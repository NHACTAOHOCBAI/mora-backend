package com.mora.backend.controller;

import com.mora.backend.model.dto.request.TestApiKeyRequest;
import com.mora.backend.model.dto.request.UserAiSettingRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.ModelQuotaResponse;
import com.mora.backend.model.dto.response.UserAiSettingResponse;
import com.mora.backend.service.UserAiSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/ai-settings")
@RequiredArgsConstructor
@Tag(name = "User AI Settings API", description = "Quản lý Cấu hình API Key và Tùy chọn Model per Agent của người dùng")
public class UserAiSettingController {

    private final UserAiSettingService userAiSettingService;

    @GetMapping
    @Operation(summary = "Lấy cấu hình AI hiện tại của người dùng")
    public ResponseEntity<ApiResponse<UserAiSettingResponse>> getSettings() {
        UserAiSettingResponse response = userAiSettingService.getSettingResponseForCurrentUser();
        return ResponseEntity.ok(
                ApiResponse.<UserAiSettingResponse>builder()
                        .message("Lấy cấu hình AI thành công")
                        .result(response)
                        .build()
        );
    }

    @PutMapping
    @Operation(summary = "Cập nhật API Key và lựa chọn Model cho các Agent")
    public ResponseEntity<ApiResponse<UserAiSettingResponse>> updateSettings(@Valid @RequestBody UserAiSettingRequest request) {
        UserAiSettingResponse response = userAiSettingService.updateSettingForCurrentUser(request);
        return ResponseEntity.ok(
                ApiResponse.<UserAiSettingResponse>builder()
                        .message("Cập nhật cấu hình AI thành công")
                        .result(response)
                        .build()
        );
    }

    @PostMapping("/test")
    @Operation(summary = "Kiểm tra kết nối và tính hợp lệ của Gemini API Key")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testApiKey(@Valid @RequestBody TestApiKeyRequest request) {
        Map<String, Object> result = userAiSettingService.testApiKey(request);
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Object>>builder()
                        .message("Kiểm tra API Key hoàn tất")
                        .result(result)
                        .build()
        );
    }

    @GetMapping("/quota")
    @Operation(summary = "Lấy thông tin hạn mức (Quota) và số lượt sử dụng trong ngày của từng Model")
    public ResponseEntity<ApiResponse<List<ModelQuotaResponse>>> getDailyQuota() {
        List<ModelQuotaResponse> quotaList = userAiSettingService.getDailyQuotaForCurrentUser();
        return ResponseEntity.ok(
                ApiResponse.<List<ModelQuotaResponse>>builder()
                        .message("Lấy thông tin hạn mức thành công")
                        .result(quotaList)
                        .build()
        );
    }
}
