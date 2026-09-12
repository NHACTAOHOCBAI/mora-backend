package com.mora.backend.service;

import com.mora.backend.model.dto.request.TestApiKeyRequest;
import com.mora.backend.model.dto.request.UserAiSettingRequest;
import com.mora.backend.model.dto.response.ModelQuotaResponse;
import com.mora.backend.model.dto.response.UserAiSettingResponse;
import com.mora.backend.model.entity.User;
import com.mora.backend.model.entity.UserAiSetting;

import java.util.List;
import java.util.Map;

public interface UserAiSettingService {
    UserAiSetting getSettingForUser(User user);
    UserAiSetting getSettingForCurrentUser();
    UserAiSettingResponse getSettingResponseForCurrentUser();
    UserAiSettingResponse updateSettingForCurrentUser(UserAiSettingRequest request);
    Map<String, Object> testApiKey(TestApiKeyRequest request);
    List<ModelQuotaResponse> getDailyQuotaForCurrentUser();
    void recordUsage(Long userId, String modelName);
}
