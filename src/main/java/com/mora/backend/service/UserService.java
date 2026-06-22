package com.mora.backend.service;

import com.mora.backend.model.dto.request.AdminUserUpdateRequest;
import com.mora.backend.model.dto.request.LoginRequest;
import com.mora.backend.model.dto.request.RegisterRequest;
import com.mora.backend.model.dto.response.AuthResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.model.dto.response.PageResponse;
import com.mora.backend.model.entity.User;

import java.util.List;

public interface UserService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    User getCurrentUser();
    UserResponse getCurrentUserResponse();
    PageResponse<UserResponse> getAllUsers(int page, int limit, String search, String sortBy, String sortOrder);
    UserResponse updateUserByAdmin(Long id, AdminUserUpdateRequest request);
}
