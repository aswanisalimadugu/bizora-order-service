package com.bizlink.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RazorpayConnectRequest {
    @NotBlank
    @Size(max = 100)
    private String keyId;

    @NotBlank
    @Size(max = 200)
    private String keySecret;

    @Size(max = 200)
    private String webhookSecret;
}
