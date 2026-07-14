package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class SubscriptionReportDto {
    /** FREE | PAID */
    private final String planTier;
    private final String planName;
    private final BigDecimal planPrice;
    private final Integer durationDays;
    private final String status;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Long daysRemaining;
    private final ProductLimitsDto productLimits;
    private final CategoryLimitsDto categoryLimits;
    /** Paid plans unlock longer income ranges + CSV export on reports. */
    private final boolean advancedReports;
    private final int maxIncomeDays;
    private final List<SubscriptionHistoryDto> history;
}
