package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionHistoryDto {
    private final UUID id;
    private final UUID planId;
    private final String planName;
    private final BigDecimal planPrice;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final String status;
    private final LocalDateTime createdAt;
}
