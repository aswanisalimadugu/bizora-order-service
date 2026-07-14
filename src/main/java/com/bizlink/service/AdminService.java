package com.bizlink.service;

import com.bizlink.exception.DuplicateException;
import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.*;
import com.bizlink.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(
            UserRepository userRepository,
            BusinessRepository businessRepository,
            CustomerRepository customerRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            PaymentRepository paymentRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
        this.customerRepository = customerRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.paymentRepository = paymentRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard() {
        log.info("Admin fetching dashboard statistics");
        verifyAdmin();

        long expired = subscriptionRepository.findAll().stream()
                .filter(s -> "EXPIRED".equals(s.getStatus())
                        || (s.getEndDate() != null && s.getEndDate().isBefore(LocalDate.now())))
                .count();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalBusinesses", businessRepository.count());
        stats.put("activeBusinesses", businessRepository.countByStatus("ACTIVE"));
        stats.put("totalCustomers", customerRepository.count());
        stats.put("activeSubscriptions", subscriptionRepository.countByStatus("ACTIVE"));
        stats.put("expiredSubscriptions", expired);
        stats.put("totalRevenue", paymentRepository.sumSuccessfulPayments());
        return stats;
    }

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        log.info("Admin fetching all users");
        verifyAdmin();
        return userRepository.findAll();
    }

    @Transactional
    public User createUser(User user) {
        log.info("Admin creating user: {}", user.getEmail());
        verifyAdmin();

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ValidationException("Email is required");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ValidationException("Password is required");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateException("Email already registered");
        }
        if (user.getMobile() != null && userRepository.existsByMobile(user.getMobile())) {
            throw new DuplicateException("Mobile number already registered");
        }

        String role = user.getRole() == null || user.getRole().isBlank()
                ? "OWNER" : user.getRole().toUpperCase();
        if (!List.of("OWNER", "ADMIN").contains(role)) {
            throw new ValidationException("Role must be OWNER or ADMIN");
        }

        user.setRole(role);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(user.getStatus() == null ? "ACTIVE" : user.getStatus());
        return userRepository.save(user);
    }

    @Transactional
    public User updateUser(UUID id, User updates) {
        log.info("Admin updating user: {}", id);
        verifyAdmin();

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (updates.getName() != null && !updates.getName().isBlank()) {
            user.setName(updates.getName());
        }
        if (updates.getMobile() != null && !updates.getMobile().isBlank()
                && !updates.getMobile().equals(user.getMobile())) {
            if (userRepository.existsByMobile(updates.getMobile())) {
                throw new DuplicateException("Mobile number already registered");
            }
            user.setMobile(updates.getMobile());
        }
        if (updates.getPassword() != null && !updates.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(updates.getPassword()));
        }
        return userRepository.save(user);
    }

    @Transactional
    public User updateUserStatus(UUID id, String status) {
        log.info("Admin updating user status: {} -> {}", id, status);
        verifyAdmin();
        validateUserStatus(status);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if ("ADMIN".equals(user.getRole())) {
            throw new ValidationException("Cannot change admin user status");
        }
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllBusinesses() {
        log.info("Admin fetching all businesses");
        verifyAdmin();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Business business : businessRepository.findAll()) {
            User owner = userRepository.findById(business.getOwnerId()).orElse(null);
            String planName = resolvePlanName(business.getId());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessId", business.getId());
            row.put("slug", business.getSlug());
            row.put("businessName", business.getBusinessName());
            row.put("ownerName", owner != null ? owner.getName() : "—");
            row.put("mobile", owner != null ? owner.getMobile() : business.getPhone());
            row.put("city", business.getCity());
            row.put("subscriptionPlan", planName);
            row.put("status", business.getStatus());
            row.put("verified", business.getVerified());
            row.put("createdDate", business.getCreatedAt());
            result.add(row);
        }
        return result;
    }

    @Transactional
    public Business createBusiness(UUID ownerId, Business business) {
        log.info("Admin creating business '{}' for owner {}", business.getBusinessName(), ownerId);
        verifyAdmin();

        if (business.getBusinessName() == null || business.getBusinessName().isBlank()) {
            throw new ValidationException("Business name is required");
        }
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found"));

        business.setOwner(owner);
        business.setOwnerId(owner.getId());
        if (business.getSlug() == null || business.getSlug().isBlank()) {
            business.setSlug(generateSlug(business.getBusinessName()));
        } else if (businessRepository.existsBySlug(business.getSlug())) {
            throw new ValidationException("Slug already exists");
        }
        if (business.getStatus() == null) business.setStatus("ACTIVE");
        if (business.getActive() == null) business.setActive(true);
        if (business.getVerified() == null) business.setVerified(false);

        return businessRepository.save(business);
    }

    private String generateSlug(String name) {
        String base = name.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "business";
        String slug = base;
        int counter = 1;
        while (businessRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }

    @Transactional(readOnly = true)
    public Business getBusiness(UUID id) {
        log.info("Admin fetching business: {}", id);
        verifyAdmin();
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        business.getCategories().size();
        business.getProducts().size();
        return business;
    }

    @Transactional
    public Business updateBusinessStatus(UUID id, String status) {
        log.info("Admin updating business status: {} -> {}", id, status);
        verifyAdmin();
        validateBusinessStatus(status);

        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        business.setStatus(status);
        switch (status) {
            case "ACTIVE" -> business.setActive(true);
            case "INACTIVE", "BLOCKED" -> business.setActive(false);
            case "VERIFIED" -> {
                business.setVerified(true);
                business.setActive(true);
                business.setStatus("ACTIVE");
            }
            default -> { }
        }
        return businessRepository.save(business);
    }

    @Transactional
    public Business verifyBusiness(UUID id) {
        log.info("Admin verifying business: {}", id);
        verifyAdmin();

        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        business.setVerified(true);
        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllSubscriptions() {
        log.info("Admin fetching all subscriptions");
        verifyAdmin();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Subscription sub : subscriptionRepository.findAllByOrderByCreatedAtDesc()) {
            Business business = businessRepository.findById(sub.getBusinessId()).orElse(null);
            SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sub.getId());
            row.put("business", business != null ? business.getBusinessName() : "—");
            row.put("businessId", sub.getBusinessId());
            row.put("plan", plan != null ? plan.getName() : "—");
            row.put("planId", sub.getPlanId());
            row.put("startDate", sub.getStartDate());
            row.put("endDate", sub.getEndDate());
            row.put("status", sub.getStatus());
            result.add(row);
        }
        return result;
    }

    @Transactional
    public SubscriptionPlan createPlan(SubscriptionPlan plan) {
        log.info("Admin creating subscription plan: {}", plan.getName());
        verifyAdmin();
        return planRepository.save(plan);
    }

    @Transactional
    public SubscriptionPlan updatePlan(UUID id, SubscriptionPlan updates) {
        log.info("Admin updating subscription plan: {}", id);
        verifyAdmin();

        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (updates.getName() != null) plan.setName(updates.getName());
        if (updates.getPrice() != null) plan.setPrice(updates.getPrice());
        if (updates.getDurationDays() != null) plan.setDurationDays(updates.getDurationDays());
        if (updates.getFeatures() != null) plan.setFeatures(updates.getFeatures());
        if (updates.getActive() != null) plan.setActive(updates.getActive());

        return planRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> getAllPlans() {
        log.info("Admin fetching all subscription plans");
        verifyAdmin();
        return planRepository.findAll();
    }

    @Transactional
    public void deletePlan(UUID id) {
        log.info("Admin deleting subscription plan: {}", id);
        verifyAdmin();
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        plan.setActive(false);
        planRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllPayments(UUID businessId) {
        log.info("Admin fetching payments (businessId={})", businessId);
        verifyAdmin();

        List<Payment> payments = paymentRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Payment payment : payments) {
            if (businessId != null && !businessId.equals(payment.getBusinessId())) {
                continue;
            }
            Business business = businessRepository.findById(payment.getBusinessId()).orElse(null);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", payment.getId());
            row.put("transactionId", payment.getTransactionId());
            row.put("business", business != null ? business.getBusinessName() : "—");
            row.put("businessId", payment.getBusinessId());
            row.put("amount", payment.getAmount());
            row.put("gateway", payment.getGateway());
            row.put("status", payment.getStatus());
            row.put("date", payment.getCreatedAt());
            result.add(row);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueReport(UUID businessId) {
        log.info("Admin fetching revenue report (businessId={})", businessId);
        verifyAdmin();

        if (businessId == null) {
            List<Map<String, Object>> monthly = new ArrayList<>();
            for (Object[] row : paymentRepository.monthlyRevenue()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("month", row[0]);
                item.put("revenue", row[1]);
                monthly.add(item);
            }
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("monthlyRevenue", monthly);
            report.put("totalRevenue", paymentRepository.sumSuccessfulPayments());
            report.put("subscriptionRevenue", paymentRepository.sumSubscriptionRevenue());
            return report;
        }

        // Business-filtered: compute in-memory from that business's payments
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal subscriptionRevenue = BigDecimal.ZERO;
        Map<String, BigDecimal> byMonth = new TreeMap<>();
        for (Payment p : paymentRepository.findByBusinessId(businessId)) {
            if (!"SUCCESS".equals(p.getStatus())) continue;
            BigDecimal amount = p.getAmount() == null ? BigDecimal.ZERO : p.getAmount();
            total = total.add(amount);
            if (p.getSubscriptionId() != null) {
                subscriptionRevenue = subscriptionRevenue.add(amount);
            }
            String month = p.getCreatedAt().toLocalDate().toString().substring(0, 7);
            byMonth.merge(month, amount, BigDecimal::add);
        }

        List<Map<String, Object>> monthly = new ArrayList<>();
        byMonth.forEach((month, revenue) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", month);
            item.put("revenue", revenue);
            monthly.add(item);
        });

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("monthlyRevenue", monthly);
        report.put("totalRevenue", total);
        report.put("subscriptionRevenue", subscriptionRevenue);
        return report;
    }

    private String resolvePlanName(UUID businessId) {
        return subscriptionRepository
                .findFirstByBusinessIdAndStatusOrderByEndDateDesc(businessId, "ACTIVE")
                .flatMap(sub -> planRepository.findById(sub.getPlanId()).map(SubscriptionPlan::getName))
                .orElse("—");
    }

    private void validateUserStatus(String status) {
        if (!List.of("ACTIVE", "BLOCKED", "INACTIVE").contains(status)) {
            throw new ValidationException("Invalid user status. Use ACTIVE, BLOCKED, or INACTIVE");
        }
    }

    private void validateBusinessStatus(String status) {
        if (!List.of("ACTIVE", "INACTIVE", "BLOCKED", "VERIFIED").contains(status)) {
            throw new ValidationException("Invalid business status");
        }
    }

    private void verifyAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            if (!"ADMIN".equals(user.getRole())) {
                throw new UnauthorizedException("Admin access required");
            }
            return;
        }
        throw new UnauthorizedException("Admin access required");
    }
}
