package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EntitlementsDto {
    private final String planTier;
    private final String planName;
    private final String status;
    private final boolean advancedReports;
    private final int maxIncomeDays;
    private final boolean canExport;
    private final boolean canUseAi;
    private final boolean canQrPrintDownload;
    private final boolean canRegenerateQr;
    private final boolean canFullAnalytics;
    /** Free: 7. Paid: null = keep history indefinitely. */
    private final Integer dataRetentionDays;
    private final ProductLimitsDto productLimits;
    private final CategoryLimitsDto categoryLimits;
    private final Long daysRemaining;
}
