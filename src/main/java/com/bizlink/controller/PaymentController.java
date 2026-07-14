package com.bizlink.controller;

import com.bizlink.dto.PaymentDto;
import com.bizlink.model.Payment;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.PaymentService;
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
@RequestMapping("/api/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody Payment payment) {
        log.info("POST /api/payment/create");
        Map<String, Object> result = paymentService.createPayment(payment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment order created", result));
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<ApiResponse<List<PaymentDto>>> getByBusiness(@PathVariable UUID businessId) {
        log.info("GET /api/payment/business/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Payments fetched",
                paymentService.getByBusinessId(businessId)));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verify(@RequestBody Map<String, String> body) {
        log.info("POST /api/payment/verify");
        paymentService.verifyAndComplete(
                body.get("razorpayOrderId"),
                body.get("razorpayPaymentId"),
                body.get("razorpaySignature"));
        return ResponseEntity.ok(ApiResponse.success("Payment verified"));
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        log.info("POST /api/payment/webhook");
        paymentService.handleWebhook(payload, signature != null ? signature : "");
        return ResponseEntity.ok(ApiResponse.success("Webhook processed"));
    }
}
