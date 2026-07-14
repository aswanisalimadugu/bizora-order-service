package com.bizlink.controller;

import com.bizlink.model.Category;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.CategoryService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Category>> create(@Valid @RequestBody Category category) {
        log.info("POST /api/categories");
        Category created = categoryService.create(category);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", created));
    }

    @GetMapping("/business/{businessId}")
    public ResponseEntity<ApiResponse<List<Category>>> getByBusiness(@PathVariable UUID businessId) {
        log.info("GET /api/categories/business/{}", businessId);
        return ResponseEntity.ok(ApiResponse.success("Categories fetched",
                categoryService.getByBusinessId(businessId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Category>> update(
            @PathVariable UUID id,
            @Valid @RequestBody Category category) {
        log.info("PUT /api/categories/{}", id);
        return ResponseEntity.ok(ApiResponse.success("Category updated",
                categoryService.update(id, category)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        log.info("DELETE /api/categories/{}", id);
        categoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted"));
    }
}
