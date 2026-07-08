package com.mora.backend.service;

import com.mora.backend.model.dto.request.AdminUserUpdateRequest;
import com.mora.backend.model.dto.request.LoginRequest;
import com.mora.backend.model.dto.request.RegisterRequest;
import com.mora.backend.model.dto.request.UpdateProfileRequest;
import com.mora.backend.model.dto.request.ChangePasswordRequest;
import com.mora.backend.model.dto.response.AuthResponse;
import com.mora.backend.model.dto.response.UserResponse;
import com.mora.backend.model.dto.response.PageResponse;
import com.mora.backend.model.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    UserResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    User getCurrentUser();
    UserResponse getCurrentUserResponse();
    PageResponse<UserResponse> getAllUsers(int page, int limit, String search, String sortBy, String sortOrder);
    UserResponse updateUserByAdmin(Long id, AdminUserUpdateRequest request);
    void deleteUser(Long id);
    UserResponse updateProfile(UpdateProfileRequest request);
    UserResponse updateAvatar(MultipartFile file);
    void changePassword(ChangePasswordRequest request);
}
