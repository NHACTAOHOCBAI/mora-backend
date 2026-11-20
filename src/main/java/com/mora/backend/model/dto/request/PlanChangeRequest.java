package com.mora.backend.model.dto.request;
import com.mora.backend.model.entity.PlanTier;
import jakarta.validation.constraints.NotNull;
public record PlanChangeRequest(@NotNull PlanTier plan) {}
