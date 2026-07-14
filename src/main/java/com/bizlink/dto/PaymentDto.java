package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PaymentDto {
    private final UUID id;
    private final UUID businessId;
    private final UUID orderId;
    private final UUID subscriptionId;
    private final String orderNumber;
    /** ORDER | SUBSCRIPTION */
    private final String type;
    private final String transactionId;
    private final String gatewayPaymentId;
    private final String gateway;
    private final BigDecimal amount;
    private final String status;
    private final LocalDateTime createdAt;
}
