package com.bizlink.controller;

import com.bizlink.dto.CancelOrderRequest;
import com.bizlink.dto.OrderTrackDto;
import com.bizlink.model.Business;
import com.bizlink.model.Order;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.BusinessService;
import com.bizlink.service.OrderService;
import com.bizlink.service.PaymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final BusinessService businessService;
    private final OrderService orderService;
    private final PaymentService paymentService;

    public PublicController(
            BusinessService businessService,
            OrderService orderService,
            PaymentService paymentService) {
        this.businessService = businessService;
        this.orderService = orderService;
        this.paymentService = paymentService;
    }

    @GetMapping("/business/{slug}")
    public ResponseEntity<ApiResponse<Business>> getBusinessBySlug(@PathVariable String slug) {
        log.info("GET /api/public/business/{}", slug);
        Business business = businessService.getBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Business fetched", business));
    }

    @GetMapping("/orders/track/{orderNumber}")
    public ResponseEntity<ApiResponse<OrderTrackDto>> trackOrder(@PathVariable String orderNumber) {
        log.info("GET /api/public/orders/track/{}", orderNumber);
        return ResponseEntity.ok(ApiResponse.success("Order fetched",
                orderService.trackByOrderNumber(orderNumber)));
    }

    @PostMapping("/orders/{orderNumber}/cancel")
    public ResponseEntity<ApiResponse<OrderTrackDto>> cancelOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody CancelOrderRequest request) {
        log.info("POST /api/public/orders/{}/cancel", orderNumber);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled",
                orderService.cancelByCustomer(orderNumber, request)));
    }

    @PostMapping("/orders/{orderId}/payment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOrderPayment(
            @PathVariable UUID orderId) {
        log.info("POST /api/public/orders/{}/payment", orderId);
        Map<String, Object> result = paymentService.createOrderPayment(orderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment order created", result));
    }

    @PostMapping("/orders/payment/verify")
    public ResponseEntity<ApiResponse<Order>> verifyOrderPayment(@RequestBody Map<String, String> body) {
        log.info("POST /api/public/orders/payment/verify");
        Order order = paymentService.verifyOrderPayment(
                body.get("razorpayOrderId"),
                body.get("razorpayPaymentId"),
                body.get("razorpaySignature"));
        return ResponseEntity.ok(ApiResponse.success("Payment verified", order));
    }
}
