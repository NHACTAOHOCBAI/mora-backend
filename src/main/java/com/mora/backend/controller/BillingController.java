package com.mora.backend.controller;
import com.mora.backend.model.dto.request.PlanChangeRequest;
import com.mora.backend.model.dto.response.*;
import com.mora.backend.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.List;
@RestController @RequestMapping("/api/billing") @RequiredArgsConstructor
public class BillingController {
    private final BillingService billing; private final UserService users;
    @GetMapping("/entitlement") public ResponseEntity<ApiResponse<EntitlementResponse>> entitlement() { return ResponseEntity.ok(ApiResponse.<EntitlementResponse>builder().result(billing.getEntitlement(users.getCurrentUser())).build()); }
    @GetMapping("/plans") public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() { return ResponseEntity.ok(ApiResponse.<List<PlanResponse>>builder().result(billing.getPlans()).build()); }
    @PostMapping("/plan-change") public ResponseEntity<ApiResponse<EntitlementResponse>> change(@Valid @RequestBody PlanChangeRequest body) { return ResponseEntity.ok(ApiResponse.<EntitlementResponse>builder().message("Đã cập nhật gói sử dụng").result(billing.changePlan(users.getCurrentUser(), body, "self_service")).build()); }
}
