package com.bizlink.service;

import com.bizlink.exception.DuplicateException;
import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.Business;
import com.bizlink.model.Category;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.CategoryRepository;
import com.bizlink.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BusinessRepository businessRepository;
    private final ProductRepository productRepository;
    private final PlanLimitService planLimitService;

    public CategoryService(
            CategoryRepository categoryRepository,
            BusinessRepository businessRepository,
            ProductRepository productRepository,
            PlanLimitService planLimitService) {
        this.categoryRepository = categoryRepository;
        this.businessRepository = businessRepository;
        this.productRepository = productRepository;
        this.planLimitService = planLimitService;
    }

    @Transactional
    public Category create(Category category) {
        log.info("Creating category for business: {}", category.getBusinessId());
        Business business = verifyBusinessAccess(category.getBusinessId());
        planLimitService.enforceCategoryLimit(category.getBusinessId());

        String name = normalizeName(category.getName());
        assertUniqueName(category.getBusinessId(), name, null);

        category.setBusiness(business);
        category.setName(name);
        return categoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public List<Category> getByBusinessId(UUID businessId) {
        log.info("Fetching categories for business: {}", businessId);
        verifyBusinessAccess(businessId);
        return categoryRepository.findByBusinessId(businessId);
    }

    @Transactional
    public Category update(UUID id, Category updates) {
        log.info("Updating category: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        verifyBusinessAccess(category.getBusinessId());

        String name = normalizeName(updates.getName());
        assertUniqueName(category.getBusinessId(), name, id);
        category.setName(name);
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting category: {}", id);
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        verifyBusinessAccess(category.getBusinessId());
        productRepository.clearCategoryId(id);
        categoryRepository.delete(category);
    }

    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("Category name is required");
        }
        return raw.trim();
    }

    private void assertUniqueName(UUID businessId, String name, UUID excludeId) {
        boolean exists = excludeId == null
                ? categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, name)
                : categoryRepository.existsByBusinessIdAndNameIgnoreCaseAndIdNot(businessId, name, excludeId);
        if (exists) {
            throw new DuplicateException("A category named \"" + name + "\" already exists");
        }
    }

    private Business verifyBusinessAccess(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        User user = getCurrentUser();
        if (!business.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new UnauthorizedException("You do not own this business");
        }
        return business;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new UnauthorizedException("Not authenticated");
    }
}
