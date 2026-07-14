package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderTrackDto {
    private final String orderNumber;
    private final String status;
    private final BigDecimal totalAmount;
    private final LocalDateTime createdAt;
    private final String businessName;
    private final String businessSlug;
    private final String paymentStatus;
    private final List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private final String productName;
        private final int quantity;
        private final BigDecimal price;
        private final String selectedOption;
    }
}
