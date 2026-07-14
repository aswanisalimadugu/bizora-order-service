package com.bizlink.controller;

import com.bizlink.model.Customer;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.CustomerService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@Valid @RequestBody Customer customer) {
        log.info("POST /api/customers");
        Customer created = customerService.create(customer);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Customer saved", created));
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<ApiResponse<List<Customer>>> getByBusiness(@PathVariable UUID businessId) {
        log.info("GET /api/customers/business/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Customers fetched",
                customerService.getByBusinessId(businessId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("DELETE /api/customers/{}", id);
        customerService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Customer deleted"));
    }
}
