package com.bizlink.controller;

import com.bizlink.dto.DashboardStatsDto;
import com.bizlink.dto.UpdateOrderItemsRequest;
import com.bizlink.model.Order;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.OrderService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Order>> create(@Valid @RequestBody Order order) {
        log.info("POST /api/orders");
        Order created = orderService.create(order);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed", created));
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<ApiResponse<List<Order>>> getByBusiness(@PathVariable UUID businessId) {
        log.info("GET /api/orders/business/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Orders fetched",
                orderService.getByBusinessId(businessId)));
    }

    @GetMapping("/business/{businessId}/stats")
    public ResponseEntity<ApiResponse<DashboardStatsDto>> getStats(@PathVariable UUID businessId) {
        log.info("GET /api/orders/business/{}/stats", businessId);
        return ResponseEntity.ok(ApiResponse.success("Dashboard stats",
                orderService.getDashboardStats(businessId)));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Order>> updateStatus(
            @PathVariable UUID id,
            @RequestParam String status) {
        log.info("PUT /api/orders/{}/status?status={}", id, status);
        return ResponseEntity.ok(ApiResponse.success("Order status updated",
                orderService.updateStatus(id, status)));
    }

    @PutMapping("/{id}/items")
    public ResponseEntity<ApiResponse<Order>> updateItems(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderItemsRequest request) {
        log.info("PUT /api/orders/{}/items", id);
        return ResponseEntity.ok(ApiResponse.success("Order items updated",
                orderService.updateItems(id, request)));
    }
}
