package com.bizlink.service;

import com.bizlink.dto.PaymentDto;
import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.Business;
import com.bizlink.model.Order;
import com.bizlink.model.Payment;
import com.bizlink.model.Subscription;
import com.bizlink.model.SubscriptionPlan;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.OrderRepository;
import com.bizlink.repository.PaymentRepository;
import com.bizlink.repository.SubscriptionPlanRepository;
import com.bizlink.repository.SubscriptionRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final BusinessRepository businessRepository;
    private final OrderRepository orderRepository;
    private final PlanLimitService planLimitService;
    private final OrderService orderService;
    private final SecretCryptoService secretCryptoService;

    public PaymentService(
            PaymentRepository paymentRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionPlanRepository planRepository,
            BusinessRepository businessRepository,
            OrderRepository orderRepository,
            PlanLimitService planLimitService,
            @Lazy OrderService orderService,
            SecretCryptoService secretCryptoService) {
        this.paymentRepository = paymentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.businessRepository = businessRepository;
        this.orderRepository = orderRepository;
        this.planLimitService = planLimitService;
        this.orderService = orderService;
        this.secretCryptoService = secretCryptoService;
    }

    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    @Value("${app.dev-mode:true}")
    private boolean devMode;

    @Value("${app.allow-platform-order-payments:true}")
    private boolean allowPlatformOrderPayments;

    @Transactional
    public Map<String, Object> createPayment(Payment payment) {
        log.info("Creating subscription payment for business: {}", payment.getBusinessId());
        verifyBusinessAccess(payment.getBusinessId());

        if (payment.getSubscriptionId() == null) {
            throw new ValidationException("Subscription is required");
        }

        Subscription subscription = subscriptionRepository.findById(payment.getSubscriptionId())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));

        if (!subscription.getBusinessId().equals(payment.getBusinessId())) {
            throw new ValidationException("Subscription does not belong to this business");
        }
        if (!"PENDING".equalsIgnoreCase(subscription.getStatus())) {
            throw new ValidationException("Only pending subscriptions can be paid");
        }

        SubscriptionPlan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        payment.setAmount(plan.getPrice());
        payment.setGateway("RAZORPAY");
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        RazorpayAccount account = resolvePlatformAccount();
        return createRazorpaySession(saved, plan.getPrice(), "sub_" + saved.getId(), account, Map.of(
                "businessId", payment.getBusinessId().toString(),
                "paymentId", saved.getId().toString(),
                "subscriptionId", payment.getSubscriptionId().toString(),
                "type", "SUBSCRIPTION"
        ));
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getByBusinessId(UUID businessId) {
        log.info("Fetching payments for business: {}", businessId);
        verifyBusinessAccess(businessId);
        LocalDateTime cutoff = planLimitService.getDataRetentionCutoff(businessId);
        List<Payment> payments = paymentRepository.findByBusinessId(businessId).stream()
                .filter(p -> {
                    if (p.getSubscriptionId() != null) {
                        return true; // billing history always kept
                    }
                    if (cutoff == null || p.getCreatedAt() == null) {
                        return true;
                    }
                    return !p.getCreatedAt().isBefore(cutoff);
                })
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();

        Set<UUID> orderIds = payments.stream()
                .map(Payment::getOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> orderNumbers = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getId, Order::getOrderNumber, (a, b) -> a));

        return payments.stream().map(p -> toDto(p, orderNumbers)).toList();
    }

    private PaymentDto toDto(Payment payment, Map<UUID, String> orderNumbers) {
        String type = payment.getOrderId() != null ? "ORDER"
                : (payment.getSubscriptionId() != null ? "SUBSCRIPTION" : "OTHER");
        return PaymentDto.builder()
                .id(payment.getId())
                .businessId(payment.getBusinessId())
                .orderId(payment.getOrderId())
                .subscriptionId(payment.getSubscriptionId())
                .orderNumber(payment.getOrderId() != null ? orderNumbers.get(payment.getOrderId()) : null)
                .type(type)
                .transactionId(payment.getTransactionId())
                .gatewayPaymentId(payment.getGatewayPaymentId())
                .gateway(payment.getGateway())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    @Transactional
    public Map<String, Object> createOrderPayment(UUID orderId) {
        log.info("Creating order payment for order: {}", orderId);
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            throw new ValidationException("Order is already paid");
        }
        if ("CANCELLED".equalsIgnoreCase(order.getStatus()) || "REJECTED".equalsIgnoreCase(order.getStatus())
                || "RECEIVED".equalsIgnoreCase(order.getStatus()) || "COMPLETED".equalsIgnoreCase(order.getStatus())) {
            throw new ValidationException("Cannot pay for a closed order");
        }

        Business business = businessRepository.findById(order.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        planLimitService.assertPubliclyVisible(business);
        RazorpayAccount account = resolveOrderAccount(business);

        Optional<Payment> existingPending = paymentRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, "PENDING");
        if (existingPending.isPresent() && existingPending.get().getTransactionId() != null
                && !existingPending.get().getTransactionId().isBlank()) {
            Payment existing = existingPending.get();
            boolean amountMatches = existing.getAmount() != null
                    && order.getTotalAmount() != null
                    && existing.getAmount().compareTo(order.getTotalAmount()) == 0;
            if (amountMatches) {
                Map<String, Object> reuse = new HashMap<>();
                reuse.put("payment", existing);
                reuse.put("keyId", account.isMock() ? null : account.getKeyId());
                reuse.put("orderId", order.getId().toString());
                reuse.put("orderNumber", order.getOrderNumber());
                reuse.put("amount", order.getTotalAmount());
                reuse.put("razorpayOrderId", existing.getTransactionId());
                reuse.put("accountSource", account.getSource());
                return reuse;
            }
            existing.setStatus("CANCELLED");
            paymentRepository.save(existing);
        }

        Payment payment = new Payment();
        payment.setBusinessId(order.getBusinessId());
        payment.setOrderId(order.getId());
        payment.setAmount(order.getTotalAmount());
        payment.setGateway("RAZORPAY");
        payment.setStatus("PENDING");
        Payment saved = paymentRepository.save(payment);

        Map<String, Object> result = createRazorpaySession(
                saved,
                order.getTotalAmount(),
                "ord_" + saved.getId(),
                account,
                Map.of(
                        "businessId", order.getBusinessId().toString(),
                        "orderId", order.getId().toString(),
                        "paymentId", saved.getId().toString(),
                        "type", "ORDER"
                ));
        result.put("orderId", order.getId().toString());
        result.put("orderNumber", order.getOrderNumber());
        return result;
    }

    private Map<String, Object> createRazorpaySession(
            Payment saved,
            BigDecimal amount,
            String receipt,
            RazorpayAccount account,
            Map<String, String> notes) {
        Map<String, Object> result = new HashMap<>();
        result.put("payment", saved);
        result.put("amount", amount);
        result.put("accountSource", account.getSource());
        result.put("keyId", account.isMock() ? null : account.getKeyId());

        if (account.isMock()) {
            log.warn("Using mock Razorpay session (dev only) for payment {}", saved.getId());
            String mockOrderId = "order_mock_" + saved.getId();
            saved.setTransactionId(mockOrderId);
            paymentRepository.save(saved);
            result.put("razorpayOrderId", mockOrderId);
            return result;
        }

        try {
            RazorpayClient client = new RazorpayClient(account.getKeyId(), account.getKeySecret());
            JSONObject options = new JSONObject();
            options.put("amount", toPaise(amount));
            options.put("currency", "INR");
            options.put("receipt", receipt);
            JSONObject notesJson = new JSONObject();
            notes.forEach(notesJson::put);
            options.put("notes", notesJson);

            com.razorpay.Order razorpayOrder = client.orders.create(options);
            saved.setTransactionId(razorpayOrder.get("id"));
            paymentRepository.save(saved);

            result.put("razorpayOrderId", razorpayOrder.get("id"));
            return result;
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed", e);
            throw new ValidationException("Payment gateway error: " + e.getMessage());
        }
    }

    @Transactional
    public Order verifyOrderPayment(String razorpayOrderId, String razorpayPaymentId, String signature) {
        log.info("Verifying order payment: {}", razorpayPaymentId);

        Payment payment = paymentRepository.findByTransactionId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getOrderId() == null) {
            throw new ValidationException("This payment is not linked to a customer order");
        }

        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            orderService.markPaid(payment.getOrderId());
            return orderRepository.findByIdWithItems(payment.getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        }

        Business business = businessRepository.findById(payment.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        RazorpayAccount account = resolveOrderAccount(business);
        assertSignature(razorpayOrderId, razorpayPaymentId, signature, account);

        payment.setGatewayPaymentId(razorpayPaymentId);
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        orderService.markPaid(payment.getOrderId());
        return orderRepository.findByIdWithItems(payment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Transactional
    public void verifyAndComplete(String razorpayOrderId, String razorpayPaymentId, String signature) {
        log.info("Verifying subscription payment: {}", razorpayPaymentId);

        Payment payment = paymentRepository.findByTransactionId(razorpayOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        verifyBusinessAccess(payment.getBusinessId());

        if ("SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            if (payment.getSubscriptionId() != null) {
                activateSubscription(payment.getSubscriptionId());
            }
            return;
        }

        RazorpayAccount account = resolvePlatformAccount();
        assertSignature(razorpayOrderId, razorpayPaymentId, signature, account);

        payment.setGatewayPaymentId(razorpayPaymentId);
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        activateSubscription(payment.getSubscriptionId());
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        log.info("Processing Razorpay webhook");

        // Always verify when a webhook secret is configured (including DEV).
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            if (signature == null || signature.isBlank()) {
                throw new ValidationException("Webhook signature is required");
            }
            String expected = hmacSha256(payload, webhookSecret);
            if (!expected.equals(signature)) {
                throw new ValidationException("Invalid webhook signature");
            }
        } else if (!devMode) {
            throw new ValidationException("Webhook secret is required");
        } else {
            log.warn("Skipping webhook signature check — no secret configured (DEV_MODE only)");
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event");

        if ("payment.captured".equals(eventType)) {
            JSONObject paymentEntity = event
                    .getJSONObject("payload")
                    .getJSONObject("payment")
                    .getJSONObject("entity");

            String orderId = paymentEntity.optString("order_id");
            String paymentId = paymentEntity.optString("id");
            int amountPaise = paymentEntity.optInt("amount", -1);

            paymentRepository.findByTransactionId(orderId).ifPresent(payment -> {
                if (amountPaise > 0 && payment.getAmount() != null) {
                    int expected = toPaise(payment.getAmount());
                    if (expected != amountPaise) {
                        log.warn("Webhook amount mismatch for payment {} expected={} got={}",
                                payment.getId(), expected, amountPaise);
                        throw new ValidationException("Payment amount mismatch");
                    }
                }
                if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
                    payment.setGatewayPaymentId(paymentId);
                    payment.setStatus("SUCCESS");
                    paymentRepository.save(payment);
                }
                if (payment.getOrderId() != null) {
                    orderService.markPaid(payment.getOrderId());
                } else {
                    activateSubscription(payment.getSubscriptionId());
                }
            });
        }
    }

    private static int toPaise(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private void assertSignature(String razorpayOrderId, String razorpayPaymentId, String signature, RazorpayAccount account) {
        if (account.isMock()) {
            if (!devMode) {
                throw new ValidationException("Mock payments are disabled");
            }
            log.warn("Accepting mock payment verify in DEV_MODE for {}", razorpayOrderId);
            return;
        }
        if (signature == null || signature.isBlank()) {
            throw new ValidationException("Payment signature is required");
        }
        String payload = razorpayOrderId + "|" + razorpayPaymentId;
        String expected = hmacSha256(payload, account.getKeySecret());
        if (!expected.equals(signature)) {
            throw new ValidationException("Invalid payment signature");
        }
    }

    private RazorpayAccount resolvePlatformAccount() {
        if (platformKeysPresent()) {
            return new RazorpayAccount(razorpayKeyId, razorpayKeySecret, "PLATFORM");
        }
        if (devMode) {
            return new RazorpayAccount(null, null, "MOCK");
        }
        throw new ValidationException("Platform payment gateway is not configured");
    }

    private RazorpayAccount resolveOrderAccount(Business business) {
        if (business.getRazorpayKeyId() != null && !business.getRazorpayKeyId().isBlank()
                && business.getRazorpayKeySecretEnc() != null && !business.getRazorpayKeySecretEnc().isBlank()) {
            String secret = secretCryptoService.decrypt(business.getRazorpayKeySecretEnc());
            return new RazorpayAccount(business.getRazorpayKeyId(), secret, "MERCHANT");
        }
        if (allowPlatformOrderPayments && platformKeysPresent()) {
            return new RazorpayAccount(razorpayKeyId, razorpayKeySecret, "PLATFORM");
        }
        if (devMode) {
            return new RazorpayAccount(null, null, "MOCK");
        }
        throw new ValidationException(
                "Online payments are not set up. Owner must connect Razorpay in Settings.");
    }

    private boolean platformKeysPresent() {
        return razorpayKeyId != null && !razorpayKeyId.isBlank()
                && razorpayKeySecret != null && !razorpayKeySecret.isBlank();
    }

    private void activateSubscription(UUID subscriptionId) {
        if (subscriptionId == null) return;
        subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
            if ("ACTIVE".equalsIgnoreCase(sub.getStatus())) {
                log.info("Subscription already active: {}", subscriptionId);
                return;
            }
            planLimitService.expireOtherActiveSubscriptions(sub.getBusinessId(), sub.getId());

            SubscriptionPlan plan = planRepository.findById(sub.getPlanId()).orElse(null);
            LocalDate start = LocalDate.now();
            sub.setStartDate(start);
            if (plan != null) {
                sub.setEndDate(start.plusDays(plan.getDurationDays()));
            } else if (sub.getEndDate() == null) {
                sub.setEndDate(start.plusDays(30));
            }

            sub.setStatus("ACTIVE");
            subscriptionRepository.save(sub);
            log.info("Subscription activated: {} until {}", subscriptionId, sub.getEndDate());
        });
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ValidationException("Signature verification failed");
        }
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
