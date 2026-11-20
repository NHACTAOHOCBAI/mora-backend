package com.mora.backend.model.dto.response;
import com.mora.backend.model.entity.PlanTier;
import lombok.*;
import java.time.LocalDateTime;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class EntitlementResponse {
    private PlanTier plan; private String planName; private int maxOwnedSpaces; private long tokenLimit;
    private long tokensUsed; private long tokensRemaining; private long ownedSpaces;
    private LocalDateTime periodStartedAt; private LocalDateTime periodEndsAt; private PlanTier scheduledPlan;
}
