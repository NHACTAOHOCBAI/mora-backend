package com.mora.backend.service.impl;
import com.mora.backend.exception.*;
import com.mora.backend.model.dto.request.PlanChangeRequest;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.model.entity.*;
import com.mora.backend.repository.*;
import com.mora.backend.service.BillingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class BillingServiceImpl implements BillingService {
    private final UserRepository users; private final SpaceRepository spaces; private final AiUsageEventRepository usage;
    private final PlanChangeHistoryRepository history;
    @Override @Transactional public EntitlementResponse getEntitlement(User user) { return response(refresh(user)); }
    @Override public List<PlanResponse> getPlans() { return Arrays.stream(PlanTier.values()).map(this::plan).toList(); }
    @Override @Transactional public EntitlementResponse changePlan(User actor, PlanChangeRequest request, String source) {
        User user = refresh(actor); PlanTier target = request.plan(); PlanTier current = user.getPlanTier();
        if (target == current) return response(user);
        if (target.ordinal() > current.ordinal()) {
            user.setPlanTier(target); user.setScheduledPlanTier(null); startNewPeriod(user);
            history.save(PlanChangeHistory.builder().userId(user.getId()).fromPlan(current).toPlan(target).source(source).build());
        } else {
            long owned = spaces.countByUserId(user.getId());
            if (owned > target.getMaxOwnedSpaces()) throw new AppException(ErrorCode.PLAN_DOWNGRADE_BLOCKED);
            user.setScheduledPlanTier(target);
        }
        return response(users.save(user));
    }
    @Override @Transactional public void requireCanCreateSpace(User user) {
        if (user.getRole() == Role.ROLE_ADMIN) return;
        User current = refresh(user);
        if (spaces.countByUserId(current.getId()) >= current.getPlanTier().getMaxOwnedSpaces()) throw new AppException(ErrorCode.SPACE_QUOTA_EXCEEDED);
    }
    @Override @Transactional public void requireCanUseAi(Space space) {
        User owner = space.getUser(); if (owner == null || owner.getRole() == Role.ROLE_ADMIN) return;
        User current = refresh(owner);
        long used = usage.sumTokensByUserAndCreatedAtGreaterThanEqual(current.getId(), current.getPlanPeriodStartedAt());
        if (used >= current.getPlanTier().getMonthlyTokenLimit()) throw new AppException(ErrorCode.AI_TOKEN_QUOTA_EXCEEDED);
    }
    private User refresh(User user) {
        User managed = users.findById(user.getId()).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (managed.getPlanTier() == null) { managed.setPlanTier(PlanTier.FREE); startNewPeriod(managed); }
        else if (managed.getPlanPeriodEndsAt() == null || !managed.getPlanPeriodEndsAt().isAfter(now)) {
            PlanTier before = managed.getPlanTier(); PlanTier next = managed.getScheduledPlanTier() == null ? before : managed.getScheduledPlanTier();
            managed.setPlanTier(next); managed.setScheduledPlanTier(null); startNewPeriod(managed);
            if (before != next) history.save(PlanChangeHistory.builder().userId(managed.getId()).fromPlan(before).toPlan(next).source("scheduled").build());
        }
        return managed;
    }
    private void startNewPeriod(User user) { LocalDateTime now = LocalDateTime.now(); user.setPlanPeriodStartedAt(now); user.setPlanPeriodEndsAt(now.plusMonths(1)); }
    private EntitlementResponse response(User user) {
        long used = usage.sumTokensByUserAndCreatedAtGreaterThanEqual(user.getId(), user.getPlanPeriodStartedAt());
        PlanTier p = user.getPlanTier(); return EntitlementResponse.builder().plan(p).planName(p.getDisplayName()).maxOwnedSpaces(p.getMaxOwnedSpaces()).tokenLimit(p.getMonthlyTokenLimit()).tokensUsed(used).tokensRemaining(Math.max(0, p.getMonthlyTokenLimit()-used)).ownedSpaces(spaces.countByUserId(user.getId())).periodStartedAt(user.getPlanPeriodStartedAt()).periodEndsAt(user.getPlanPeriodEndsAt()).scheduledPlan(user.getScheduledPlanTier()).build();
    }
    private PlanResponse plan(PlanTier p) { return PlanResponse.builder().code(p).name(p.getDisplayName()).maxOwnedSpaces(p.getMaxOwnedSpaces()).tokenLimit(p.getMonthlyTokenLimit()).build(); }
}
