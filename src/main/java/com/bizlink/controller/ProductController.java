package com.bizlink.controller;

import com.bizlink.model.Product;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.ProductService;
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
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Product>> create(
            @RequestPart("product") Product product,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("POST /api/products");
        Product created = productService.create(product, image);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created", created));
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<ApiResponse<List<Product>>> getByBusiness(@PathVariable UUID businessId) {
        log.info("GET /api/products/business/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Products fetched",
                productService.getByBusinessId(businessId)));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Product>> update(
            @PathVariable UUID id,
            @RequestPart("product") Product product,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("PUT /api/products/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Product updated",
                productService.update(id, product, image)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("DELETE /api/products/{}", id);
        productService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted"));
    }
}
