package com.bizlink.controller;

import com.bizlink.dto.CategoryLimitsDto;
import com.bizlink.dto.ProductLimitsDto;
import com.bizlink.dto.SubscriptionReportDto;
import com.bizlink.model.Subscription;
import com.bizlink.model.SubscriptionPlan;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.SubscriptionService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getPlans() {
        log.info("GET /api/subscription/plans");
        return ResponseEntity.ok(ApiResponse.success("Plans fetched",
                subscriptionService.getActivePlans()));
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Subscription>> create(@Valid @RequestBody Subscription subscription) {
        log.info("POST /api/subscription/create");
        Subscription created = subscriptionService.create(subscription);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Subscription created", created));
    }

    @GetMapping("/active/{businessId}")
    public ResponseEntity<ApiResponse<Subscription>> getActive(@PathVariable UUID businessId) {
        log.info("GET /api/subscription/active/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Active subscription",
                subscriptionService.getActiveSubscription(businessId)));
    }

    @GetMapping("/report/{businessId}")
    public ResponseEntity<ApiResponse<SubscriptionReportDto>> getReport(@PathVariable UUID businessId) {
        log.info("GET /api/subscription/report/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Subscription report",
                subscriptionService.getSubscriptionReport(businessId)));
    }

    @GetMapping("/entitlements/{businessId}")
    public ResponseEntity<ApiResponse<com.bizlink.dto.EntitlementsDto>> getEntitlements(
            @PathVariable UUID businessId) {
        log.info("GET /api/subscription/entitlements/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Entitlements",
                subscriptionService.getEntitlements(businessId)));
    }

    @GetMapping("/limits/{businessId}")
    public ResponseEntity<ApiResponse<ProductLimitsDto>> getLimits(@PathVariable UUID businessId) {
        log.info("GET /api/subscription/limits/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Product limits",
                subscriptionService.getProductLimits(businessId)));
    }

    @GetMapping("/category-limits/{businessId}")
    public ResponseEntity<ApiResponse<CategoryLimitsDto>> getCategoryLimits(@PathVariable UUID businessId) {
        log.info("GET /api/subscription/category-limits/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Category limits",
                subscriptionService.getCategoryLimits(businessId)));
    }
}
