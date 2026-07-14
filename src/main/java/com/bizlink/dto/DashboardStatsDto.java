package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
public class DashboardStatsDto {
    private final UUID businessId;
    private final long productCount;
    private final long orderCount;
    private final long pendingOrders;
    private final BigDecimal paidRevenue;
    private final BigDecimal todayIncome;
    private final String subscriptionStatus;
    private final String planName;
    private final boolean paidPlan;
}
