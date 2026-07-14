package com.bizlink.service;

import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.dto.CategoryLimitsDto;
import com.bizlink.dto.EntitlementsDto;
import com.bizlink.dto.ProductLimitsDto;
import com.bizlink.dto.SubscriptionHistoryDto;
import com.bizlink.dto.SubscriptionReportDto;
import com.bizlink.model.Business;
import com.bizlink.model.Subscription;
import com.bizlink.model.SubscriptionPlan;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.SubscriptionPlanRepository;
import com.bizlink.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BusinessRepository businessRepository;
    private final PlanLimitService planLimitService;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            BusinessRepository businessRepository,
            PlanLimitService planLimitService) {
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.businessRepository = businessRepository;
        this.planLimitService = planLimitService;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getActivePlans() {
        log.info("Fetching active subscription plans");
        return planRepository.findByActiveTrue();
    }

    @Transactional
    public Subscription create(Subscription subscription) {
        log.info("Creating subscription for business: {}", subscription.getBusinessId());
        verifyBusinessAccess(subscription.getBusinessId());

        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription plan not found"));

        if (Boolean.FALSE.equals(plan.getActive())) {
            throw new ValidationException("Selected plan is not active");
        }

        boolean isFree = plan.getPrice().compareTo(BigDecimal.ZERO) == 0;

        // Reuse an open PENDING for the same plan instead of creating duplicates.
        if (!isFree) {
            var existingPending = subscriptionRepository
                    .findFirstByBusinessIdAndStatusAndPlanIdOrderByCreatedAtDesc(
                            subscription.getBusinessId(), "PENDING", plan.getId());
            if (existingPending.isPresent()) {
                log.info("Reusing pending subscription {} for plan {}", existingPending.get().getId(), plan.getId());
                return existingPending.get();
            }
            // Cancel other abandoned PENDING checkouts for this business.
            subscriptionRepository.findByBusinessId(subscription.getBusinessId()).stream()
                    .filter(s -> "PENDING".equalsIgnoreCase(s.getStatus()))
                    .forEach(s -> {
                        s.setStatus("CANCELLED");
                        subscriptionRepository.save(s);
                    });
        }

        // Billing window starts on activation (payment), not on checkout create.
        subscription.setStartDate(null);
        subscription.setEndDate(null);

        if (isFree) {
            planLimitService.expireOtherActiveSubscriptions(subscription.getBusinessId(), null);
            var start = java.time.LocalDate.now();
            subscription.setStartDate(start);
            subscription.setEndDate(start.plusDays(plan.getDurationDays()));
            subscription.setStatus("ACTIVE");
        } else {
            subscription.setStatus("PENDING");
        }

        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Subscription getActiveSubscription(UUID businessId) {
        log.info("Fetching active subscription for business: {}", businessId);
        verifyBusinessAccess(businessId);
        return planLimitService.getValidActiveSubscription(businessId);
    }

    @Transactional(readOnly = true)
    public ProductLimitsDto getProductLimits(UUID businessId) {
        verifyBusinessAccess(businessId);
        return planLimitService.getProductLimits(businessId);
    }

    @Transactional(readOnly = true)
    public CategoryLimitsDto getCategoryLimits(UUID businessId) {
        verifyBusinessAccess(businessId);
        return planLimitService.getCategoryLimits(businessId);
    }

    @Transactional
    public EntitlementsDto getEntitlements(UUID businessId) {
        log.info("Building entitlements for business: {}", businessId);
        verifyBusinessAccess(businessId);

        Subscription active = planLimitService.getValidActiveSubscription(businessId);
        ProductLimitsDto productLimits = planLimitService.getProductLimits(businessId);
        CategoryLimitsDto categoryLimits = planLimitService.getCategoryLimits(businessId);

        String planTier = "FREE";
        String planName = "Free plan";
        String status = "FREE";
        Long daysRemaining = null;
        boolean advanced = false;
        int maxIncomeDays = 7;

        if (active != null) {
            status = active.getStatus();
            if (active.getEndDate() != null) {
                daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), active.getEndDate()));
            }
            SubscriptionPlan plan = planRepository.findById(active.getPlanId()).orElse(null);
            if (plan != null) {
                planName = plan.getName();
                boolean paid = plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) > 0;
                planTier = paid ? "PAID" : "FREE";
                advanced = paid && "ACTIVE".equalsIgnoreCase(active.getStatus());
                maxIncomeDays = advanced ? 90 : 7;
            }
        }

        return EntitlementsDto.builder()
                .planTier(planTier)
                .planName(planName)
                .status(status)
                .advancedReports(advanced)
                .maxIncomeDays(maxIncomeDays)
                .canExport(advanced)
                .canUseAi(advanced)
                .canQrPrintDownload(advanced)
                .canRegenerateQr(advanced)
                .canFullAnalytics(advanced)
                .dataRetentionDays(advanced ? null : 7)
                .productLimits(productLimits)
                .categoryLimits(categoryLimits)
                .daysRemaining(daysRemaining)
                .build();
    }

    @Transactional
    public SubscriptionReportDto getSubscriptionReport(UUID businessId) {
        log.info("Building subscription report for business: {}", businessId);
        verifyBusinessAccess(businessId);

        Subscription active = planLimitService.getValidActiveSubscription(businessId);
        ProductLimitsDto productLimits = planLimitService.getProductLimits(businessId);
        CategoryLimitsDto categoryLimits = planLimitService.getCategoryLimits(businessId);

        String planTier = "FREE";
        String planName = "Free plan";
        BigDecimal planPrice = BigDecimal.ZERO;
        Integer durationDays = null;
        String status = "FREE";
        LocalDate startDate = null;
        LocalDate endDate = null;
        Long daysRemaining = null;
        boolean advancedReports = false;
        int maxIncomeDays = 7;

        if (active != null) {
            SubscriptionPlan plan = planRepository.findById(active.getPlanId()).orElse(null);
            status = active.getStatus();
            startDate = active.getStartDate();
            endDate = active.getEndDate();
            if (endDate != null) {
                daysRemaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate));
            }
            if (plan != null) {
                planName = plan.getName();
                planPrice = plan.getPrice() != null ? plan.getPrice() : BigDecimal.ZERO;
                durationDays = plan.getDurationDays();
                boolean paid = planPrice.compareTo(BigDecimal.ZERO) > 0;
                planTier = paid ? "PAID" : "FREE";
                advancedReports = paid && "ACTIVE".equalsIgnoreCase(active.getStatus());
                maxIncomeDays = advancedReports ? 90 : 7;
            }
        }

        List<SubscriptionHistoryDto> history = subscriptionRepository.findByBusinessId(businessId).stream()
                .sorted(Comparator.comparing(Subscription::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toHistoryDto)
                .toList();

        return SubscriptionReportDto.builder()
                .planTier(planTier)
                .planName(planName)
                .planPrice(planPrice)
                .durationDays(durationDays)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .daysRemaining(daysRemaining)
                .productLimits(productLimits)
                .categoryLimits(categoryLimits)
                .advancedReports(advancedReports)
                .maxIncomeDays(maxIncomeDays)
                .history(history)
                .build();
    }

    private SubscriptionHistoryDto toHistoryDto(Subscription sub) {
        SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
        return SubscriptionHistoryDto.builder()
                .id(sub.getId())
                .planId(sub.getPlanId())
                .planName(plan != null ? plan.getName() : "Unknown plan")
                .planPrice(plan != null ? plan.getPrice() : null)
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .status(sub.getStatus())
                .createdAt(sub.getCreatedAt())
                .build();
    }

    private void verifyBusinessAccess(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        User user = getCurrentUser();
        if (!business.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new UnauthorizedException("You do not own this business");
        }
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new UnauthorizedException("Not authenticated");
    }
}
