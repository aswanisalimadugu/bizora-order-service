package com.bizlink.controller;

import com.bizlink.dto.RazorpayConnectRequest;
import com.bizlink.dto.RazorpayStatusDto;
import com.bizlink.model.Business;
import com.bizlink.model.BusinessQr;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.BusinessService;
import com.bizlink.service.PlanLimitService;
import com.bizlink.service.QrCodeService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
public class BusinessController {

    private final BusinessService businessService;
    private final QrCodeService qrCodeService;
    private final PlanLimitService planLimitService;

    public BusinessController(
            BusinessService businessService,
            QrCodeService qrCodeService,
            PlanLimitService planLimitService) {
        this.businessService = businessService;
        this.qrCodeService = qrCodeService;
        this.planLimitService = planLimitService;
    }

    @PostMapping(value = "/api/business", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Business>> create(
            @RequestPart("business") Business business,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        log.info("POST /api/business");
        Business created = businessService.create(business, logo, cover);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Business created", created));
    }

    @GetMapping("/api/business/{id}")
    public ResponseEntity<ApiResponse<Business>> getById(@PathVariable UUID id) {
        log.info("GET /api/business/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Business fetched", businessService.getById(id)));
    }

    @GetMapping("/api/business/my")
    public ResponseEntity<ApiResponse<List<Business>>> getMyBusinesses() {
        log.info("GET /api/business/my");
        return ResponseEntity.ok(ApiResponse.success("Businesses fetched", businessService.getByOwner()));
    }

    @PutMapping(value = "/api/business/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Business>> update(
            @PathVariable UUID id,
            @RequestPart("business") Business business,
            @RequestPart(value = "logo", required = false) MultipartFile logo,
            @RequestPart(value = "cover", required = false) MultipartFile cover) {
        log.info("PUT /api/business/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Business updated",
                businessService.update(id, business, logo, cover)));
    }

    @DeleteMapping("/api/business/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("DELETE /api/business/{}", id);
        businessService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Business deleted"));
    }

    @GetMapping("/api/business/{id}/qr")
    public ResponseEntity<ApiResponse<BusinessQr>> getQr(@PathVariable UUID id) {
        log.info("GET /api/business/{}/qr", id);
        Business business = businessService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("QR fetched", qrCodeService.getOrCreate(business)));
    }

    @PostMapping("/api/business/{id}/qr/regenerate")
    public ResponseEntity<ApiResponse<BusinessQr>> regenerateQr(@PathVariable UUID id) {
        log.info("POST /api/business/{}/qr/regenerate", id);
        planLimitService.assertPaidFeature(id, "QR regenerate");
        Business business = businessService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("QR regenerated", qrCodeService.regenerate(business)));
    }

    @GetMapping("/api/business/{id}/razorpay")
    public ResponseEntity<ApiResponse<RazorpayStatusDto>> getRazorpay(@PathVariable UUID id) {
        log.info("GET /api/business/{}/razorpay", id);
        return ResponseEntity.ok(ApiResponse.success("Razorpay status", businessService.getRazorpayStatus(id)));
    }

    @PutMapping("/api/business/{id}/razorpay")
    public ResponseEntity<ApiResponse<RazorpayStatusDto>> connectRazorpay(
            @PathVariable UUID id,
            @Valid @RequestBody RazorpayConnectRequest request) {
        log.info("PUT /api/business/{}/razorpay", id);
        return ResponseEntity.ok(ApiResponse.success("Razorpay connected",
                businessService.connectRazorpay(id, request)));
    }

    @DeleteMapping("/api/business/{id}/razorpay")
    public ResponseEntity<ApiResponse<RazorpayStatusDto>> disconnectRazorpay(@PathVariable UUID id) {
        log.info("DELETE /api/business/{}/razorpay", id);
        return ResponseEntity.ok(ApiResponse.success("Razorpay disconnected",
                businessService.disconnectRazorpay(id)));
    }
}
