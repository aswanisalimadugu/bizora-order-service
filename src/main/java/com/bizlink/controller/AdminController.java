package com.bizlink.controller;

import com.bizlink.model.Business;
import com.bizlink.model.SubscriptionPlan;
import com.bizlink.model.User;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.AdminService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> dashboard() {
        log.info("GET /api/admin/dashboard");
        return ResponseEntity.ok(ApiResponse.success("Dashboard fetched", adminService.getDashboard()));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<User>>> getUsers() {
        log.info("GET /api/admin/users");
        return ResponseEntity.ok(ApiResponse.success("Users fetched", adminService.getAllUsers()));
    }

    @PostMapping("/user")
    public ResponseEntity<ApiResponse<User>> createUser(@RequestBody User user) {
        log.info("POST /api/admin/user");
        User created = adminService.createUser(user);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created", created));
    }

    @PutMapping("/user/{id}")
    public ResponseEntity<ApiResponse<User>> updateUser(
            @PathVariable UUID id,
            @RequestBody User user) {
        log.info("PUT /api/admin/user/{}", id);
        return ResponseEntity.ok(ApiResponse.success("User updated",
                adminService.updateUser(id, user)));
    }

    @PutMapping("/user/{id}/status")
    public ResponseEntity<ApiResponse<User>> updateUserStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        log.info("PUT /api/admin/user/{}/status?status={}", id, status);
        return ResponseEntity.ok(ApiResponse.success("User status updated",
                adminService.updateUserStatus(id, status)));
    }

    @GetMapping("/businesses")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getBusinesses() {
        log.info("GET /api/admin/businesses");
        return ResponseEntity.ok(ApiResponse.success("Businesses fetched", adminService.getAllBusinesses()));
    }

    @PostMapping("/business")
    public ResponseEntity<ApiResponse<Business>> createBusiness(@RequestBody Map<String, Object> body) {
        log.info("POST /api/admin/business");
        UUID ownerId = UUID.fromString((String) body.get("ownerId"));
        Business business = new Business();
        business.setBusinessName((String) body.get("businessName"));
        if (body.get("description") != null) business.setDescription((String) body.get("description"));
        if (body.get("phone") != null) business.setPhone((String) body.get("phone"));
        if (body.get("whatsappNumber") != null) business.setWhatsappNumber((String) body.get("whatsappNumber"));
        if (body.get("city") != null) business.setCity((String) body.get("city"));
        if (body.get("state") != null) business.setState((String) body.get("state"));
        if (body.get("pincode") != null) business.setPincode((String) body.get("pincode"));
        if (body.get("slug") != null) business.setSlug((String) body.get("slug"));
        Business created = adminService.createBusiness(ownerId, business);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Business created", created));
    }

    @GetMapping("/business/{id}")
    public ResponseEntity<ApiResponse<Business>> getBusiness(@PathVariable UUID id) {
        log.info("GET /api/admin/business/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Business fetched", adminService.getBusiness(id)));
    }

    @PutMapping("/business/{id}/status")
    public ResponseEntity<ApiResponse<Business>> updateBusinessStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        log.info("PUT /api/admin/business/{}/status?status={}", id, status);
        return ResponseEntity.ok(ApiResponse.success("Business status updated",
                adminService.updateBusinessStatus(id, status)));
    }

    @PutMapping("/business/{id}/verify")
    public ResponseEntity<ApiResponse<Business>> verifyBusiness(@PathVariable UUID id) {
        log.info("PUT /api/admin/business/{}/verify", id);
        return ResponseEntity.ok(ApiResponse.success("Business verified",
                adminService.verifyBusiness(id)));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getSubscriptions() {
        log.info("GET /api/admin/subscriptions");
        return ResponseEntity.ok(ApiResponse.success("Subscriptions fetched",
                adminService.getAllSubscriptions()));
    }

    @PostMapping("/subscription-plan")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> createPlan(
            @Valid @RequestBody SubscriptionPlan plan) {
        log.info("POST /api/admin/subscription-plan");
        SubscriptionPlan created = adminService.createPlan(plan);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Plan created", created));
    }

    @PutMapping("/subscription-plan/{id}")
    public ResponseEntity<ApiResponse<SubscriptionPlan>> updatePlan(
            @PathVariable UUID id,
            @RequestBody SubscriptionPlan plan) {
        log.info("PUT /api/admin/subscription-plan/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Plan updated",
                adminService.updatePlan(id, plan)));
    }

    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlan>>> getPlans() {
        log.info("GET /api/admin/plans");
        return ResponseEntity.ok(ApiResponse.success("Plans fetched", adminService.getAllPlans()));
    }

    @DeleteMapping("/subscription-plan/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable UUID id) {
        log.info("DELETE /api/admin/subscription-plan/{}", id);
        adminService.deletePlan(id);
        return ResponseEntity.ok(ApiResponse.success("Plan deactivated"));
    }

    @GetMapping("/payments")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPayments(
            @RequestParam(required = false) UUID businessId) {
        log.info("GET /api/admin/payments?businessId={}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched",
                adminService.getAllPayments(businessId)));
    }

    @GetMapping("/reports/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> revenueReport(
            @RequestParam(required = false) UUID businessId) {
        log.info("GET /api/admin/reports/revenue?businessId={}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Revenue report fetched",
                adminService.getRevenueReport(businessId)));
    }
}
