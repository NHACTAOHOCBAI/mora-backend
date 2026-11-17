package com.mora.backend.controller;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/member-invitations") @RequiredArgsConstructor
public class MemberInvitationController {
 private final SpaceService service;
 @PostMapping("/{id}/accept") public ResponseEntity<ApiResponse<SpaceMemberInvitationResponse>> accept(@PathVariable Long id) { return ResponseEntity.ok(ApiResponse.<SpaceMemberInvitationResponse>builder().result(service.acceptMemberInvitation(id)).build()); }
 @PostMapping("/{id}/decline") public ResponseEntity<ApiResponse<SpaceMemberInvitationResponse>> decline(@PathVariable Long id) { return ResponseEntity.ok(ApiResponse.<SpaceMemberInvitationResponse>builder().result(service.declineMemberInvitation(id)).build()); }
}
