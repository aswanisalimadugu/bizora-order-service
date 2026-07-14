package com.bizlink.service;

import lombok.Getter;

/**
 * Resolved Razorpay credentials for creating/verifying a payment.
 */
@Getter
public class RazorpayAccount {
    private final String keyId;
    private final String keySecret;
    private final String source; // PLATFORM | MERCHANT | MOCK

    public RazorpayAccount(String keyId, String keySecret, String source) {
        this.keyId = keyId;
        this.keySecret = keySecret;
        this.source = source;
    }

    public boolean isMock() {
        return "MOCK".equals(source);
    }

    public boolean hasKeys() {
        return keyId != null && !keyId.isBlank() && keySecret != null && !keySecret.isBlank();
    }
}
