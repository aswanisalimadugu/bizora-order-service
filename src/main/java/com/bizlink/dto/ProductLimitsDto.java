package com.bizlink.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductLimitsDto {
    private final long current;
    private final int max;
    private final boolean unlimited;
}
