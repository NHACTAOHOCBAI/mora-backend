package com.mora.backend.service;

import java.time.LocalDateTime;
import java.util.Map;

public interface AiSettingsService {
    record ModelPrice(double inputPerMillionUsd, double cachedInputPerMillionUsd, double outputPerMillionUsd) {}
    record SettingsView(boolean apiKeyConfigured, String keyFingerprint, boolean benchmarkApiKeyConfigured,
                        String benchmarkKeyFingerprint, String chatModel, String parserModel,
                        String embeddingModel, String benchmarkJudgeModel, String benchmarkJudgeEmbeddingModel,
                        double vndPerUsd, String pricingVersion,
                        Map<String, ModelPrice> modelPrices, LocalDateTime updatedAt) {}
    record UpdateRequest(String apiKey, String benchmarkApiKey, String chatModel, String parserModel, String embeddingModel,
                         String benchmarkJudgeModel, String benchmarkJudgeEmbeddingModel,
                         double vndPerUsd, String pricingVersion, Map<String, ModelPrice> modelPrices) {}
    record RuntimeConfig(String apiKey, String benchmarkApiKey, String chatModel, String parserModel, String embeddingModel,
                         String benchmarkJudgeModel, String benchmarkJudgeEmbeddingModel,
                         double vndPerUsd, String pricingVersion, Map<String, ModelPrice> modelPrices) {}
    SettingsView get();
    SettingsView update(UpdateRequest request);
    RuntimeConfig runtime();
}
