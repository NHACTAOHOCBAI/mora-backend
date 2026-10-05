package com.mora.backend.controller;

import com.mora.backend.model.dto.request.SpaceCreateRequest;
import com.mora.backend.model.dto.request.SpaceInvitationCreateRequest;
import com.mora.backend.model.dto.request.SpaceMemberAddRequest;
import com.mora.backend.model.dto.request.SpaceMemberRoleUpdateRequest;
import com.mora.backend.model.dto.response.*;
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
@Tag(name = "Space API", description = "Các API liên quan đến quản lý Không gian học tập (Spaces) và Cộng tác nhóm")
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
    @Operation(summary = "Lấy danh sách tất cả các Không gian học tập mà người dùng có quyền truy cập")
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
    @Operation(summary = "Lấy thông tin chi tiết một Không gian học tập kèm tài liệu và thành viên")
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
    @Operation(summary = "Xóa một Không gian học tập (Chỉ OWNER hoặc ADMIN)")
    public ResponseEntity<ApiResponse<Void>> deleteSpace(@PathVariable("id") Long id) {
        spaceService.deleteSpace(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Xóa Không gian học tập thành công")
                        .build()
        );
    }

    // ==========================================
    // TẦNG CỘNG TÁC: QUẢN LÝ THÀNH VIÊN (MEMBERS)
    // ==========================================

    @GetMapping("/{id}/members")
    @Operation(summary = "Lấy danh sách thành viên trong Không gian học tập")
    public ResponseEntity<ApiResponse<List<SpaceMemberResponse>>> getSpaceMembers(@PathVariable("id") Long id) {
        List<SpaceMemberResponse> members = spaceService.getSpaceMembers(id);
        return ResponseEntity.ok(
                ApiResponse.<List<SpaceMemberResponse>>builder()
                        .message("Lấy danh sách thành viên thành công")
                        .result(members)
                        .build()
        );
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Thêm thành viên vào Không gian học tập bằng username/email (Chỉ OWNER)")
    public ResponseEntity<ApiResponse<SpaceMemberResponse>> addSpaceMember(
            @PathVariable("id") Long id,
            @Valid @RequestBody SpaceMemberAddRequest request) {
        SpaceMemberResponse response = spaceService.addSpaceMember(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SpaceMemberResponse>builder()
                        .message("Thêm thành viên vào nhóm thành công")
                        .result(response)
                        .build()
        );
    }

    @PatchMapping("/{id}/members/{userId}")
    @Operation(summary = "Cập nhật vai trò thành viên trong Không gian (Chỉ OWNER)")
    public ResponseEntity<ApiResponse<SpaceMemberResponse>> updateMemberRole(
            @PathVariable("id") Long id,
            @PathVariable("userId") Long userId,
            @Valid @RequestBody SpaceMemberRoleUpdateRequest request) {
        SpaceMemberResponse response = spaceService.updateMemberRole(id, userId, request);
        return ResponseEntity.ok(
                ApiResponse.<SpaceMemberResponse>builder()
                        .message("Cập nhật vai trò thành viên thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "Xóa thành viên khỏi Không gian hoặc tự rời nhóm")
    public ResponseEntity<ApiResponse<Void>> removeSpaceMember(
            @PathVariable("id") Long id,
            @PathVariable("userId") Long userId) {
        spaceService.removeSpaceMember(id, userId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Đã rời hoặc xóa thành viên khỏi Không gian thành công")
                        .build()
        );
    }

    // ==========================================
    // TẦNG CỘNG TÁC: LỜI MỜI THAM GIA (INVITATIONS)
    // ==========================================

    @PostMapping("/{id}/invitations")
    @Operation(summary = "Tạo mã mời / link mời tham gia Không gian (Chỉ OWNER)")
    public ResponseEntity<ApiResponse<SpaceInvitationResponse>> createInvitation(
            @PathVariable("id") Long id,
            @Valid @RequestBody SpaceInvitationCreateRequest request) {
        SpaceInvitationResponse response = spaceService.createInvitation(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<SpaceInvitationResponse>builder()
                        .message("Tạo mã mời thành công")
                        .result(response)
                        .build()
        );
    }

    @GetMapping("/{id}/invitations")
    @Operation(summary = "Lấy danh sách mã mời của Không gian (Chỉ OWNER)")
    public ResponseEntity<ApiResponse<List<SpaceInvitationResponse>>> getSpaceInvitations(@PathVariable("id") Long id) {
        List<SpaceInvitationResponse> response = spaceService.getSpaceInvitations(id);
        return ResponseEntity.ok(
                ApiResponse.<List<SpaceInvitationResponse>>builder()
                        .message("Lấy danh sách mã mời thành công")
                        .result(response)
                        .build()
        );
    }

    @DeleteMapping("/{id}/invitations/{invitationId}")
    @Operation(summary = "Hủy hiệu lực mã mời (Chỉ OWNER)")
    public ResponseEntity<ApiResponse<Void>> revokeInvitation(
            @PathVariable("id") Long id,
            @PathVariable("invitationId") Long invitationId) {
        spaceService.revokeInvitation(id, invitationId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .message("Hủy mã mời thành công")
                        .build()
        );
    }

    @GetMapping("/join/{inviteCode}")
    @Operation(summary = "Xem trước thông tin Không gian từ mã mời")
    public ResponseEntity<ApiResponse<SpaceJoinPreviewResponse>> previewInvitation(@PathVariable("inviteCode") String inviteCode) {
        SpaceJoinPreviewResponse response = spaceService.previewInvitation(inviteCode);
        return ResponseEntity.ok(
                ApiResponse.<SpaceJoinPreviewResponse>builder()
                        .message("Lấy thông tin mã mời thành công")
                        .result(response)
                        .build()
        );
    }

    @PostMapping("/join/{inviteCode}")
    @Operation(summary = "Xác nhận tham gia Không gian qua mã mời")
    public ResponseEntity<ApiResponse<SpaceResponse>> joinSpaceByInviteCode(@PathVariable("inviteCode") String inviteCode) {
        SpaceResponse response = spaceService.joinSpaceByInviteCode(inviteCode);
        return ResponseEntity.ok(
                ApiResponse.<SpaceResponse>builder()
                        .message("Tham gia Không gian học tập thành công")
                        .result(response)
                        .build()
        );
    }
}
