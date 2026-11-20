package com.mora.backend.service.impl;

import com.mora.backend.model.entity.AiUsageEvent;
import com.mora.backend.repository.AiUsageEventRepository;
import com.mora.backend.repository.SpaceRepository;
import com.mora.backend.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class AiUsageServiceImpl implements AiUsageService {
    private final AiUsageEventRepository events;
    private final SpaceRepository spaces;
    @Override @Transactional
    public void record(List<UsageItem> items, Long spaceId, Long documentId) {
        if (items == null || items.isEmpty()) return;
        events.saveAll(items.stream().filter(item -> item != null && item.totalTokens() >= 0)
                .map(item -> AiUsageEvent.builder().operation(safe(item.operation(), "unknown")).model(safe(item.model(), "unknown"))
                        .inputTokens(Math.max(0, item.inputTokens())).outputTokens(Math.max(0, item.outputTokens()))
                        .cachedInputTokens(Math.max(0, item.cachedInputTokens())).totalTokens(Math.max(0, item.totalTokens()))
                        .estimatedCostVnd(item.estimatedCostVnd() == null ? java.math.BigDecimal.ZERO : item.estimatedCostVnd())
                        .pricingVersion(item.pricingVersion()).spaceId(spaceId).documentId(documentId)
                        .userId(spaceId == null ? null : spaces.findById(spaceId).map(s -> s.getUser() == null ? null : s.getUser().getId()).orElse(null)).build()).toList());
    }
    private String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.substring(0, Math.min(value.length(), 128)); }
}
