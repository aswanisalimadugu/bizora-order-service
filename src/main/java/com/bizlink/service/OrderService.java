package com.bizlink.service;

import com.bizlink.dto.CancelOrderRequest;
import com.bizlink.dto.DashboardStatsDto;
import com.bizlink.dto.OrderTrackDto;
import com.bizlink.dto.UpdateOrderItemsRequest;
import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.*;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.CustomerRepository;
import com.bizlink.repository.OrderRepository;
import com.bizlink.repository.PaymentRepository;
import com.bizlink.repository.ProductRepository;
import com.bizlink.repository.SubscriptionPlanRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {

    private static final Set<String> VALID_STATUSES = Set.of(
            "PENDING", "ACCEPTED", "COMPLETED", "RECEIVED", "REJECTED", "CANCELLED");
    private static final Set<String> EDITABLE_STATUSES = Set.of("PENDING", "ACCEPTED");
    private static final Set<String> CLOSED_STATUSES = Set.of("RECEIVED", "REJECTED", "CANCELLED");

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING", Set.of("ACCEPTED", "REJECTED", "CANCELLED"),
            "ACCEPTED", Set.of("COMPLETED", "CANCELLED"),
            "COMPLETED", Set.of("RECEIVED"),
            "RECEIVED", Set.of(),
            "REJECTED", Set.of(),
            "CANCELLED", Set.of()
    );

    private final OrderRepository orderRepository;
    private final BusinessRepository businessRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final PlanLimitService planLimitService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public OrderService(
            OrderRepository orderRepository,
            BusinessRepository businessRepository,
            ProductRepository productRepository,
            CustomerRepository customerRepository,
            PaymentRepository paymentRepository,
            PlanLimitService planLimitService,
            SubscriptionPlanRepository subscriptionPlanRepository) {
        this.orderRepository = orderRepository;
        this.businessRepository = businessRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.planLimitService = planLimitService;
        this.subscriptionPlanRepository = subscriptionPlanRepository;
    }

    @Transactional
    public Order create(Order order) {
        log.info("Creating order for business: {}", order.getBusinessId());

        if (order.getBusinessId() == null) {
            throw new ValidationException("Business is required");
        }
        if (order.getCustomerId() == null) {
            throw new ValidationException("Customer is required");
        }

        Business business = businessRepository.findById(order.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        planLimitService.assertPubliclyVisible(business);

        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!customer.getBusinessId().equals(order.getBusinessId())) {
            throw new ValidationException("Customer does not belong to this business");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new ValidationException("Order must have at least one item");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));

            if (!product.getBusinessId().equals(order.getBusinessId())) {
                throw new ValidationException("Product does not belong to this business");
            }
            if (Boolean.FALSE.equals(product.getAvailable())) {
                throw new ValidationException("Product not available: " + product.getName());
            }

            item.setPrice(product.getPrice());
            item.setOrder(order);
            if (item.getQuantity() <= 0) {
                throw new ValidationException("Quantity must be at least 1");
            }
            if (item.getSelectedOption() != null && !item.getSelectedOption().isBlank()) {
                String selected = item.getSelectedOption().trim();
                if (!optionAllowed(product.getOptions(), selected)) {
                    throw new ValidationException("Invalid option for " + product.getName() + ": " + selected);
                }
                item.setSelectedOption(selected);
            } else {
                item.setSelectedOption(null);
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        order.setTotalAmount(total);
        order.setOrderNumber(generateOrderNumber());
        order.setStatus("PENDING");
        order.setPaymentStatus("UNPAID");

        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<Order> getByBusinessId(UUID businessId) {
        log.info("Fetching orders for business: {}", businessId);
        verifyBusinessAccess(businessId);
        LocalDateTime cutoff = planLimitService.getDataRetentionCutoff(businessId);
        List<Order> orders = cutoff == null
                ? orderRepository.findByBusinessIdOrderByCreatedAtDesc(businessId)
                : orderRepository.findByBusinessIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(businessId, cutoff);
        orders.forEach(o -> o.getItems().size());
        return orders;
    }

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(UUID businessId) {
        verifyBusinessAccess(businessId);
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime cutoff = planLimitService.getDataRetentionCutoff(businessId);
        Subscription active = planLimitService.getValidActiveSubscription(businessId);
        String subStatus = "NONE";
        String planName = "Free plan";
        boolean paidPlan = false;
        if (active != null) {
            subStatus = active.getStatus();
            SubscriptionPlan plan = subscriptionPlanRepository.findById(active.getPlanId()).orElse(null);
            if (plan != null) {
                planName = plan.getName();
                paidPlan = plan.getPrice() != null && plan.getPrice().compareTo(BigDecimal.ZERO) > 0;
            }
        }
        long orderCount = cutoff == null
                ? orderRepository.countByBusinessId(businessId)
                : orderRepository.countByBusinessIdAndCreatedAtGreaterThanEqual(businessId, cutoff);
        long pendingOrders = cutoff == null
                ? orderRepository.countByBusinessIdAndStatusIgnoreCase(businessId, "PENDING")
                : orderRepository.countByBusinessIdAndStatusIgnoreCaseAndCreatedAtGreaterThanEqual(
                        businessId, "PENDING", cutoff);
        BigDecimal paidRevenue = cutoff == null
                ? orderRepository.sumPaidRevenue(businessId)
                : orderRepository.sumPaidRevenueSince(businessId, cutoff);
        return DashboardStatsDto.builder()
                .businessId(businessId)
                .productCount(productRepository.countByBusinessId(businessId))
                .orderCount(orderCount)
                .pendingOrders(pendingOrders)
                .paidRevenue(paidRevenue)
                .todayIncome(orderRepository.sumPaidRevenueSince(businessId, startOfDay))
                .subscriptionStatus(subStatus)
                .planName(planName)
                .paidPlan(paidPlan)
                .build();
    }

    @Transactional(readOnly = true)
    public OrderTrackDto trackByOrderNumber(String orderNumber) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new ValidationException("Order number is required");
        }
        Order order = orderRepository.findByOrderNumberWithItems(orderNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        Business business = businessRepository.findById(order.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        // Tracking existing orders must work even if the shop subscription expired.
        if (Boolean.FALSE.equals(business.getActive())) {
            throw new ResourceNotFoundException("Order not found");
        }

        List<OrderTrackDto.Item> items = order.getItems().stream().map(item -> {
            String name = productRepository.findById(item.getProductId())
                    .map(Product::getName)
                    .orElse("Item");
            return OrderTrackDto.Item.builder()
                    .productName(name)
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .selectedOption(item.getSelectedOption())
                    .build();
        }).toList();

        return OrderTrackDto.builder()
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .businessName(business.getBusinessName())
                .businessSlug(business.getSlug())
                .items(items)
                .build();
    }

    @Transactional
    public void markPaid(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            return;
        }
        order.setPaymentStatus("PAID");
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getByIdPublic(UUID id) {
        return orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Transactional
    public Order updateStatus(UUID id, String status) {
        log.info("Updating order status: {} -> {}", id, status);
        if (status == null || !VALID_STATUSES.contains(status.toUpperCase())) {
            throw new ValidationException(
                    "Invalid status. Use: PENDING, ACCEPTED, COMPLETED, RECEIVED, REJECTED, CANCELLED");
        }
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        verifyBusinessAccess(order.getBusinessId());

        String next = status.toUpperCase();
        String current = order.getStatus() == null ? "PENDING" : order.getStatus().toUpperCase();

        if ("ACCEPTED".equals(next) && !"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ValidationException("Order must be paid before it can be accepted");
        }

        assertTransition(current, next);
        order.setStatus(next);
        return orderRepository.save(order);
    }

    @Transactional
    public OrderTrackDto cancelByCustomer(String orderNumber, CancelOrderRequest request) {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new ValidationException("Order number is required");
        }
        String mobile = normalizeMobile(request.getMobile());
        if (mobile == null) {
            throw new ValidationException("Valid 10-digit mobile number is required");
        }

        Order order = orderRepository.findByOrderNumberWithItems(orderNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        String current = order.getStatus() == null ? "PENDING" : order.getStatus().toUpperCase();
        if (!"PENDING".equals(current)) {
            throw new ValidationException("Only pending orders can be cancelled");
        }
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ValidationException(
                    "Paid orders cannot be cancelled here — please contact the store");
        }

        Customer customer = customerRepository.findById(order.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (!mobile.equals(customer.getMobile())) {
            throw new ValidationException("Mobile number does not match this order");
        }

        order.setStatus("CANCELLED");
        orderRepository.save(order);
        return trackByOrderNumber(order.getOrderNumber());
    }

    @Transactional
    public Order updateItems(UUID id, UpdateOrderItemsRequest request) {
        log.info("Updating items for order: {}", id);
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        verifyBusinessAccess(order.getBusinessId());

        String current = order.getStatus() == null ? "" : order.getStatus().toUpperCase();
        if (!EDITABLE_STATUSES.contains(current)) {
            throw new ValidationException("Only PENDING or ACCEPTED orders can be edited");
        }
        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ValidationException("Paid orders cannot be edited — refund or cancel with the customer first");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ValidationException("Order must have at least one item — cancel the order instead");
        }

        Set<UUID> existingProductIds = order.getItems().stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toCollection(HashSet::new));

        order.getItems().clear();

        BigDecimal total = BigDecimal.ZERO;
        for (UpdateOrderItemsRequest.Item reqItem : request.getItems()) {
            if (reqItem.getQuantity() <= 0) {
                throw new ValidationException("Quantity must be at least 1");
            }
            Product product = productRepository.findById(reqItem.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + reqItem.getProductId()));

            if (!product.getBusinessId().equals(order.getBusinessId())) {
                throw new ValidationException("Product does not belong to this business");
            }
            boolean isExistingLine = existingProductIds.contains(product.getId());
            if (!isExistingLine && Boolean.FALSE.equals(product.getAvailable())) {
                throw new ValidationException("Product not available: " + product.getName());
            }

            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(product.getId());
            item.setQuantity(reqItem.getQuantity());
            item.setPrice(product.getPrice());
            if (reqItem.getSelectedOption() != null && !reqItem.getSelectedOption().isBlank()) {
                String selected = reqItem.getSelectedOption().trim();
                if (!optionAllowed(product.getOptions(), selected)) {
                    throw new ValidationException("Invalid option for " + product.getName() + ": " + selected);
                }
                item.setSelectedOption(selected);
            }
            order.getItems().add(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(reqItem.getQuantity())));
        }

        BigDecimal previousTotal = order.getTotalAmount();
        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        if (previousTotal == null || previousTotal.compareTo(total) != 0) {
            List<Payment> pending = paymentRepository.findByOrderIdAndStatus(saved.getId(), "PENDING");
            for (Payment p : pending) {
                p.setStatus("CANCELLED");
                paymentRepository.save(p);
            }
        }
        return saved;
    }

    private boolean optionAllowed(String productOptions, String selected) {
        if (productOptions == null || productOptions.isBlank()) {
            return false;
        }
        for (String opt : productOptions.split(",")) {
            if (opt.trim().equalsIgnoreCase(selected)) {
                return true;
            }
        }
        return false;
    }

    private void assertTransition(String current, String next) {
        if (current.equals(next)) {
            return;
        }
        if (CLOSED_STATUSES.contains(current)) {
            throw new ValidationException("Closed orders cannot change status");
        }
        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new ValidationException("Invalid status transition: " + current + " → " + next);
        }
    }

    private void verifyBusinessAccess(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            if (!business.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
                throw new UnauthorizedException("You do not own this business");
            }
            return;
        }
        throw new UnauthorizedException("Not authenticated");
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null) return null;
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10) return null;
        return digits;
    }

    private String generateOrderNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "ORD-" + timestamp + "-" + random;
    }
}
