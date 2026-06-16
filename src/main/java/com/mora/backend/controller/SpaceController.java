package com.mora.backend.controller;

import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.response.ApiResponse;
import com.mora.backend.model.dto.response.SpaceDetailResponse;
import com.mora.backend.model.dto.response.SpaceResponse;
import com.mora.backend.service.SpaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/spaces")
@RequiredArgsConstructor
@Tag(name = "Space API", description = "Các API liên quan đến quản lý Không gian học tập (Spaces)")
public class SpaceController {

    private final SpaceService spaceService;

    @PostMapping
    @Operation(summary = "Tạo một Không gian học tập mới")
    public ResponseEntity<ApiResponse<SpaceResponse>> createSpace(@Valid @RequestBody SpaceCreateRequest request) {
        SpaceResponse response = spaceService.createSpace(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SpaceResponse>builder()
                        .message("Tạo Không gian học tập thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách tất cả các Không gian học tập")
    public ResponseEntity<ApiResponse<List<SpaceResponse>>> getAllSpaces() {
        List<SpaceResponse> response = spaceService.getAllSpaces();
        return ResponseEntity.ok(
                ApiResponse.<List<SpaceResponse>>builder()
                        .message("Lấy danh sách Không gian học tập thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy thông tin chi tiết một Không gian học tập kèm tài liệu")
    public ResponseEntity<ApiResponse<SpaceDetailResponse>> getSpaceById(@PathVariable("id") Long id) {
        SpaceDetailResponse response = spaceService.getSpaceById(id);
        return ResponseEntity.ok(
                ApiResponse.<SpaceDetailResponse>builder()
                        .message("Lấy thông tin Không gian học tập thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa một Không gian học tập")
    public ResponseEntity<ApiResponse<Void>> deleteSpace(@PathVariable("id") Long id) {
        spaceService.deleteSpace(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa Không gian học tập thành công")
                        .build()
        );
    }
}
