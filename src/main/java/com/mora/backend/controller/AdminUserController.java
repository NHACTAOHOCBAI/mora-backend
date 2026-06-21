package com.mora.backend.controller;

import com.mora.backend.model.dto.request.AdminUserUpdateRequest;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateUserByAdmin(id, request));
    }
}
