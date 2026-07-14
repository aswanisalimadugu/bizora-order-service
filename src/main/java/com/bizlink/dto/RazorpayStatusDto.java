package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RazorpayStatusDto {
    private final String paymentMode;
    private final String keyId;
    private final boolean configured;
    private final boolean hasWebhookSecret;
    /** Platform can collect order payments when merchant keys missing. */
    private final boolean platformOrderPaymentsAllowed;
}
