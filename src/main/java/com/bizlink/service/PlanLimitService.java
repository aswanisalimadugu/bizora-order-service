package com.bizlink.service;

import com.bizlink.dto.CategoryLimitsDto;
import com.bizlink.dto.ProductLimitsDto;
import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.SubscriptionRequiredException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.Business;
import com.bizlink.model.Subscription;
import com.bizlink.model.SubscriptionPlan;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.CategoryRepository;
import com.bizlink.repository.ProductRepository;
import com.bizlink.repository.SubscriptionPlanRepository;
import com.bizlink.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class PlanLimitService {

    private static final int TRIAL_MAX_PRODUCTS = 5;
    private static final int TRIAL_MAX_CATEGORIES = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessRepository businessRepository;

    public PlanLimitService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            BusinessRepository businessRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.businessRepository = businessRepository;
    }

    @Transactional(readOnly = true)
    public void enforceProductLimit(UUID businessId) {
        long current = productRepository.countByBusinessId(businessId);
        int max = getMaxProducts(businessId);
        if (current >= max) {
            throw new ValidationException(
                    "Product limit reached (" + max + "). Upgrade your subscription to add more products.");
        }
    }

    @Transactional(readOnly = true)
    public int getMaxProducts(UUID businessId) {
        Subscription subscription = getValidActiveSubscription(businessId);
        if (subscription == null) {
            return TRIAL_MAX_PRODUCTS;
        }
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        if (plan == null || plan.getMaxProducts() == null) {
            return Integer.MAX_VALUE;
        }
        return plan.getMaxProducts();
    }

    @Transactional(readOnly = true)
    public void enforceCategoryLimit(UUID businessId) {
        long current = categoryRepository.countByBusinessId(businessId);
        int max = getMaxCategories(businessId);
        if (current >= max) {
            throw new ValidationException(
                    "Category limit reached (" + max + "). Upgrade your subscription to add more categories.");
        }
    }

    @Transactional(readOnly = true)
    public int getMaxCategories(UUID businessId) {
        Subscription subscription = getValidActiveSubscription(businessId);
        if (subscription == null) {
            return TRIAL_MAX_CATEGORIES;
        }
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        if (plan == null || plan.getMaxCategories() == null) {
            return Integer.MAX_VALUE;
        }
        return plan.getMaxCategories();
    }

    @Transactional(readOnly = true)
    public CategoryLimitsDto getCategoryLimits(UUID businessId) {
        long current = categoryRepository.countByBusinessId(businessId);
        int max = getMaxCategories(businessId);
        boolean unlimited = max == Integer.MAX_VALUE;
        return CategoryLimitsDto.builder()
                .current(current)
                .max(unlimited ? -1 : max)
                .unlimited(unlimited)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductLimitsDto getProductLimits(UUID businessId) {
        long current = productRepository.countByBusinessId(businessId);
        int max = getMaxProducts(businessId);
        boolean unlimited = max == Integer.MAX_VALUE;
        return ProductLimitsDto.builder()
                .current(current)
                .max(unlimited ? -1 : max)
                .unlimited(unlimited)
                .build();
    }

    @Transactional
    public Subscription getValidActiveSubscription(UUID businessId) {
        return subscriptionRepository
                .findFirstByBusinessIdAndStatusOrderByEndDateDesc(businessId, "ACTIVE")
                .map(this::expireIfNeeded)
                .orElse(null);
    }

    @Transactional
    public void assertPubliclyVisible(Business business) {
        if (Boolean.FALSE.equals(business.getActive())) {
            throw new ResourceNotFoundException("Business not found");
        }
        if (business.getStatus() != null && !"ACTIVE".equalsIgnoreCase(business.getStatus())) {
            throw new ResourceNotFoundException("Business not found");
        }
        // Free trial (no paid plan) is allowed — catalog limits still apply via getMaxProducts/Categories.
    }

    /**
     * True when business has an ACTIVE paid subscription (price &gt; 0).
     */
    @Transactional
    public boolean isPaidActivePlan(UUID businessId) {
        Subscription subscription = getValidActiveSubscription(businessId);
        if (subscription == null) {
            return false;
        }
        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId()).orElse(null);
        if (plan == null || plan.getPrice() == null) {
            return false;
        }
        return plan.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0;
    }

    /** Free / unpaid: keep last 7 days. Paid: null = unlimited retention. */
    @Transactional
    public java.time.LocalDateTime getDataRetentionCutoff(UUID businessId) {
        if (isPaidActivePlan(businessId)) {
            return null;
        }
        return java.time.LocalDateTime.now().minusDays(7);
    }

    /**
     * Optional hard gate for paid-only features. Free trial shops stay public with trial limits.
     */
    @Transactional
    public void assertPaidFeature(UUID businessId, String featureName) {
        if (!isPaidActivePlan(businessId)) {
            throw new SubscriptionRequiredException(
                    featureName + " requires a paid plan. Upgrade to unlock.");
        }
    }

    @Transactional
    public void assertHasActiveSubscription(UUID businessId) {
        if (getValidActiveSubscription(businessId) == null) {
            throw new SubscriptionRequiredException(
                    "Active subscription required. Please subscribe or renew to continue.");
        }
    }

    @Transactional
    public Business getPublicBusiness(String slug) {
        Business business = businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        assertPubliclyVisible(business);
        return business;
    }

    @Transactional
    public void expireOtherActiveSubscriptions(UUID businessId, UUID exceptId) {
        subscriptionRepository.findByBusinessId(businessId).stream()
                .filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus()))
                .filter(s -> exceptId == null || !exceptId.equals(s.getId()))
                .forEach(s -> {
                    s.setStatus("EXPIRED");
                    subscriptionRepository.save(s);
                    log.info("Expired previous subscription {} for business {}", s.getId(), businessId);
                });
    }

    private Subscription expireIfNeeded(Subscription sub) {
        if (sub.getEndDate() != null && sub.getEndDate().isBefore(LocalDate.now())) {
            sub.setStatus("EXPIRED");
            subscriptionRepository.save(sub);
            log.info("Subscription {} expired on {}", sub.getId(), sub.getEndDate());
            return null;
        }
        return sub;
    }
}
