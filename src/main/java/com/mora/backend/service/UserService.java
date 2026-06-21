package com.mora.backend.service;

import com.mora.backend.model.dto.request.AdminUserUpdateRequest;
import com.mora.backend.model.dto.request.LoginRequest;
import com.mora.backend.model.dto.request.RegisterRequest;
import com.mora.backend.model.dto.response.AuthResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.model.entity.User;

import java.util.List;

public interface UserService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    User getCurrentUser();
    UserResponse getCurrentUserResponse();
    List<UserResponse> getAllUsers();
    UserResponse updateUserByAdmin(Long id, AdminUserUpdateRequest request);
}
