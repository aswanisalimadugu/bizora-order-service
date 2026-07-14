package com.bizlink.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "transaction_id", length = 150)
    private String transactionId;

    /** Gateway capture/payment id (e.g. Razorpay pay_xxx). Order id stays in transactionId. */
    @Column(name = "gateway_payment_id", length = 150)
    private String gatewayPaymentId;

    @Column(length = 50)
    private String gateway;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 30)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (gateway == null) {
            gateway = "RAZORPAY";
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
