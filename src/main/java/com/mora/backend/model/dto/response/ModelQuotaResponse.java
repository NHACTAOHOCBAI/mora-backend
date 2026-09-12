package com.mora.backend.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelQuotaResponse {

    private String modelName;
    private String displayName;
    private String agentRole;
    private int todayRequests;
    private int dailyLimit;
    private double usagePercent;
    private String status; // "SAFE", "WARNING", "DANGER"
    private String recommendation;
}
