package com.bizlink.service;

import com.bizlink.model.Business;
import com.bizlink.model.Order;
import com.bizlink.model.Payment;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.CustomerRepository;
import com.bizlink.repository.OrderRepository;
import com.bizlink.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Free / unpaid shops keep only the last 7 days of order data.
 * Older orders (+ related order payments) and orphan customers are purged.
 * Subscription/billing payments are never deleted.
 */
@Slf4j
@Service
public class FreePlanDataRetentionService {

    private final BusinessRepository businessRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final PlanLimitService planLimitService;

    public FreePlanDataRetentionService(
            BusinessRepository businessRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            PlanLimitService planLimitService) {
        this.businessRepository = businessRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.planLimitService = planLimitService;
    }

    @Transactional
    public int purgeAllFreeBusinesses() {
        int total = 0;
        for (Business business : businessRepository.findAll()) {
            total += purgeBusinessIfFree(business.getId());
        }
        log.info("Free-plan retention sweep finished — deleted {} old orders across businesses", total);
        return total;
    }

    @Transactional
    public int purgeBusinessIfFree(UUID businessId) {
        if (planLimitService.isPaidActivePlan(businessId)) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        List<Order> oldOrders = orderRepository.findByBusinessIdAndCreatedAtBefore(businessId, cutoff);
        if (oldOrders.isEmpty()) {
            return pruneOrphanCustomers(businessId, cutoff);
        }
        List<UUID> orderIds = oldOrders.stream().map(Order::getId).toList();
        List<Payment> orderPayments = paymentRepository.findByOrderIdIn(orderIds);
        paymentRepository.deleteAll(orderPayments);
        orderRepository.deleteAll(oldOrders);
        int customers = pruneOrphanCustomers(businessId, cutoff);
        log.info("Free-plan retention: business {} deleted {} orders, {} payments, {} customers",
                businessId, oldOrders.size(), orderPayments.size(), customers);
        return oldOrders.size();
    }

    private int pruneOrphanCustomers(UUID businessId, LocalDateTime cutoff) {
        var customers = customerRepository.findByBusinessId(businessId);
        int removed = 0;
        for (var c : customers) {
            if (c.getCreatedAt() != null && c.getCreatedAt().isAfter(cutoff)) {
                continue;
            }
            long remainingOrders = orderRepository.countByBusinessIdAndCustomerId(businessId, c.getId());
            if (remainingOrders == 0) {
                customerRepository.delete(c);
                removed++;
            }
        }
        return removed;
    }
}
