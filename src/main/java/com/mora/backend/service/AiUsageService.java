package com.mora.backend.service;

import java.util.List;

public interface AiUsageService {
    void record(List<UsageItem> items, Long spaceId, Long documentId);
    record UsageItem(String operation, String model, long inputTokens, long outputTokens, long cachedInputTokens,
                     long totalTokens, java.math.BigDecimal estimatedCostVnd, String pricingVersion) {}
}
