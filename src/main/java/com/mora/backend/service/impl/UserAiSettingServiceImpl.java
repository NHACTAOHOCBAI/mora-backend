package com.mora.backend.service.impl;

import com.mora.backend.client.AiServiceClient;
import com.mora.backend.model.dto.request.TestApiKeyRequest;
import com.mora.backend.model.dto.request.UserAiSettingRequest;
import com.mora.backend.model.dto.response.ModelQuotaResponse;
import com.mora.backend.model.dto.response.UserAiSettingResponse;
import com.mora.backend.model.entity.User;
import com.mora.backend.model.entity.UserAiDailyUsage;
import com.mora.backend.model.entity.UserAiSetting;
import com.mora.backend.repository.UserAiDailyUsageRepository;
import com.mora.backend.repository.UserAiSettingRepository;
import com.mora.backend.service.UserAiSettingService;
import com.mora.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAiSettingServiceImpl implements UserAiSettingService {

    private final UserAiSettingRepository userAiSettingRepository;
    private final UserAiDailyUsageRepository userAiDailyUsageRepository;
    private final UserService userService;
    private final AiServiceClient aiServiceClient;

    private static final Map<String, Integer> MODEL_DAILY_LIMITS = Map.of(
            "gemini-3.1-flash-lite", 500,
            "gemini-3.5-flash-lite", 500,
            "gemini-2.5-flash", 20,
            "gemini-3.5-flash", 20,
            "gemini-3.7-flash", 20,
            "gemini-2.5-pro", 20
    );

    private static final Map<String, String> MODEL_DISPLAY_NAMES = Map.of(
            "gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite",
            "gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite",
            "gemini-2.5-flash", "Gemini 2.5 Flash",
            "gemini-3.5-flash", "Gemini 3.5 Flash",
            "gemini-3.7-flash", "Gemini 3.7 Flash",
            "gemini-2.5-pro", "Gemini 2.5 Pro"
    );

    @Override
    @Transactional(readOnly = true)
    public UserAiSetting getSettingForUser(User user) {
        return userAiSettingRepository.findByUserId(user.getId())
                .orElseGet(() -> UserAiSetting.builder()
                        .user(user)
                        .geminiApiKey(null)
                        .chatModel("gemini-3.5-flash-lite")
                        .routerModel("gemini-3.1-flash-lite")
                        .evaluatorModel("gemini-3.1-flash-lite")
                        .parserModel("gemini-3.5-flash-lite")
                        .summarizerModel("gemini-3.1-flash-lite")
                        .temperature(0.0)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public UserAiSetting getSettingForCurrentUser() {
        User user = userService.getCurrentUser();
        return getSettingForUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserAiSettingResponse getSettingResponseForCurrentUser() {
        UserAiSetting setting = getSettingForCurrentUser();
        return mapToResponse(setting);
    }

    @Override
    @Transactional
    public UserAiSettingResponse updateSettingForCurrentUser(UserAiSettingRequest request) {
        User user = userService.getCurrentUser();
        UserAiSetting setting = userAiSettingRepository.findByUserId(user.getId())
                .orElseGet(() -> UserAiSetting.builder().user(user).build());

        String newKey = request.getGeminiApiKey();
        if (newKey != null && !newKey.isBlank() && !newKey.contains("****")) {
            setting.setGeminiApiKey(newKey.trim());
        }

        if (request.getChatModel() != null && !request.getChatModel().isBlank()) {
            setting.setChatModel(request.getChatModel().trim());
        }
        if (request.getRouterModel() != null && !request.getRouterModel().isBlank()) {
            setting.setRouterModel(request.getRouterModel().trim());
        }
        if (request.getEvaluatorModel() != null && !request.getEvaluatorModel().isBlank()) {
            setting.setEvaluatorModel(request.getEvaluatorModel().trim());
        }
        if (request.getParserModel() != null && !request.getParserModel().isBlank()) {
            setting.setParserModel(request.getParserModel().trim());
        }
        if (request.getSummarizerModel() != null && !request.getSummarizerModel().isBlank()) {
            setting.setSummarizerModel(request.getSummarizerModel().trim());
        }
        if (request.getTemperature() != null) {
            setting.setTemperature(request.getTemperature());
        }

        UserAiSetting saved = userAiSettingRepository.save(setting);
        log.info("Updated AI settings for user: {}", user.getUsername());
        return mapToResponse(saved);
    }

    @Override
    public Map<String, Object> testApiKey(TestApiKeyRequest request) {
        AiServiceClient.PythonValidateKeyResponse res = aiServiceClient.callValidateKey(
                request.getApiKey(),
                request.getModelName() != null ? request.getModelName() : "gemini-2.5-flash"
        );
        return Map.of(
                "valid", res.valid,
                "message", res.message
        );
    }

    @Override
    @Transactional
    public void recordUsage(Long userId, String modelName) {
        if (userId == null || modelName == null || modelName.isBlank()) return;
        LocalDate today = LocalDate.now();
        String normalizedModel = modelName.trim().toLowerCase();

        try {
            UserAiDailyUsage usage = userAiDailyUsageRepository
                    .findByUserIdAndModelNameAndUsageDate(userId, normalizedModel, today)
                    .orElseGet(() -> UserAiDailyUsage.builder()
                            .userId(userId)
                            .modelName(normalizedModel)
                            .usageDate(today)
                            .requestCount(0)
                            .build());

            usage.setRequestCount(usage.getRequestCount() + 1);
            userAiDailyUsageRepository.save(usage);
        } catch (Exception e) {
            log.error("Failed to record model usage for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ModelQuotaResponse> getDailyQuotaForCurrentUser() {
        User user = userService.getCurrentUser();
        UserAiSetting setting = getSettingForUser(user);
        LocalDate today = LocalDate.now();

        List<UserAiDailyUsage> usages = userAiDailyUsageRepository.findByUserIdAndUsageDate(user.getId(), today);
        Map<String, Integer> usageMap = new HashMap<>();
        for (UserAiDailyUsage u : usages) {
            usageMap.put(u.getModelName().toLowerCase(), u.getRequestCount());
        }

        List<ModelQuotaResponse> quotaList = new ArrayList<>();
        List<String> orderedModels = List.of(
                "gemini-3.7-flash",
                "gemini-3.5-flash",
                "gemini-2.5-flash",
                "gemini-3.1-flash-lite",
                "gemini-3.5-flash-lite",
                "gemini-2.5-pro"
        );

        for (String modelId : orderedModels) {
            int limit = MODEL_DAILY_LIMITS.getOrDefault(modelId, 1500);
            String displayName = MODEL_DISPLAY_NAMES.getOrDefault(modelId, modelId);
            int count = usageMap.getOrDefault(modelId.toLowerCase(), 0);
            double percent = Math.min(100.0, Math.round(((double) count / limit * 100.0) * 10.0) / 10.0);

            String status = "SAFE";
            String recommendation = "Hạn mức ổn định.";
            if (percent >= 80.0) {
                status = "DANGER";
                recommendation = "Đã dùng hơn 80% hạn mức hôm nay. Hãy cân nhắc đổi sang Gemini 3.1 Flash Lite để không bị gián đoạn.";
            } else if (percent >= 50.0) {
                status = "WARNING";
                recommendation = "Đã dùng hơn 50% hạn mức hôm nay. Hãy theo dõi khi sử dụng liên tục.";
            }

            // Determine Agent roles assigned to this model
            List<String> assignedRoles = new ArrayList<>();
            if (modelId.equalsIgnoreCase(setting.getChatModel())) assignedRoles.add("Chat Chính");
            if (modelId.equalsIgnoreCase(setting.getRouterModel())) assignedRoles.add("Router");
            if (modelId.equalsIgnoreCase(setting.getEvaluatorModel())) assignedRoles.add("QC Evaluator");
            if (modelId.equalsIgnoreCase(setting.getParserModel())) assignedRoles.add("Parser PDF");
            if (modelId.equalsIgnoreCase(setting.getSummarizerModel())) assignedRoles.add("Tóm Tắt");

            String roleStr = assignedRoles.isEmpty() ? "Chưa gán" : String.join(", ", assignedRoles);

            quotaList.add(ModelQuotaResponse.builder()
                    .modelName(modelId)
                    .displayName(displayName)
                    .agentRole(roleStr)
                    .todayRequests(count)
                    .dailyLimit(limit)
                    .usagePercent(percent)
                    .status(status)
                    .recommendation(recommendation)
                    .build());
        }

        return quotaList;
    }

    private UserAiSettingResponse mapToResponse(UserAiSetting setting) {
        String key = setting.getGeminiApiKey();
        boolean hasKey = (key != null && !key.isBlank());
        String maskedKey = "";
        if (hasKey) {
            if (key.length() > 10) {
                maskedKey = key.substring(0, 6) + "****" + key.substring(key.length() - 4);
            } else {
                maskedKey = "****";
            }
        }

        return UserAiSettingResponse.builder()
                .hasApiKey(hasKey)
                .maskedApiKey(maskedKey)
                .chatModel(setting.getChatModel())
                .routerModel(setting.getRouterModel())
                .evaluatorModel(setting.getEvaluatorModel())
                .parserModel(setting.getParserModel())
                .summarizerModel(setting.getSummarizerModel())
                .temperature(setting.getTemperature())
                .build();
    }
}
