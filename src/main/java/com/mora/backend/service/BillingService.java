package com.mora.backend.service;
import com.mora.backend.model.dto.request.PlanChangeRequest;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.model.entity.*;
import java.util.List;
public interface BillingService {
    EntitlementResponse getEntitlement(User user);
    List<PlanResponse> getPlans();
    EntitlementResponse changePlan(User user, PlanChangeRequest request, String source);
    void requireCanCreateSpace(User user);
    void requireCanUseAi(Space space);
}
