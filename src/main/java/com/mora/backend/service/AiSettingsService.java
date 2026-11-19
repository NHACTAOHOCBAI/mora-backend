package com.mora.backend.service;

import com.mora.backend.model.dto.response.PageResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AiSettingsService {
    enum ChangeGroup { CHAT, BENCHMARK, PRICING }
    record SettingsChange(ChangeGroup group, String field, String model, String before, String after) {}
    record HistoryEntry(long id, Instant savedAt, long actorId, String actorName, List<SettingsChange> changes) {}
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
    PageResponse<HistoryEntry> history(int page);
    RuntimeConfig runtime();
}
