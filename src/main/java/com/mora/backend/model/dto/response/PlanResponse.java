package com.mora.backend.model.dto.response;
import com.mora.backend.model.entity.PlanTier;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PlanResponse { private PlanTier code; private String name; private int maxOwnedSpaces; private long tokenLimit; }
