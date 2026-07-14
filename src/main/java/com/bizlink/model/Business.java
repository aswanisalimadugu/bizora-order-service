package com.bizlink.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "business")
@Getter
@Setter
@NoArgsConstructor
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    @JsonIgnore
    private User owner;

    @Column(name = "owner_id", insertable = false, updatable = false)
    private UUID ownerId;

    @NotBlank
    @Size(max = 150)
    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Size(max = 150)
    @Column(unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Size(max = 15)
    private String phone;

    @Column(name = "whatsapp_number", length = 15)
    private String whatsappNumber;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @Size(max = 10)
    private String pincode;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Size(max = 500)
    @Column(name = "business_hours")
    private String businessHours;

    @Column(name = "is_open")
    private Boolean isOpen = true;

    private Boolean active = true;

    private Boolean verified = false;

    @Size(max = 30)
    private String status = "ACTIVE";

    /** Public Razorpay Key ID for this merchant (customer order payments). */
    @JsonIgnore
    @Size(max = 100)
    @Column(name = "razorpay_key_id")
    private String razorpayKeyId;

    /** AES-GCM encrypted Razorpay Key Secret — never expose to API clients. */
    @JsonIgnore
    @Column(name = "razorpay_key_secret_enc", columnDefinition = "TEXT")
    private String razorpayKeySecretEnc;

    @JsonIgnore
    @Column(name = "razorpay_webhook_secret_enc", columnDefinition = "TEXT")
    private String razorpayWebhookSecretEnc;

    /**
     * NOT_CONFIGURED | MERCHANT_DIRECT | PLATFORM_HOLD
     */
    @Size(max = 30)
    @Column(name = "payment_mode", nullable = false)
    private String paymentMode = "NOT_CONFIGURED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "business", fetch = FetchType.LAZY)
    @JsonManagedReference("business-categories")
    private List<Category> categories = new ArrayList<>();

    @OneToMany(mappedBy = "business", fetch = FetchType.LAZY)
    @JsonManagedReference("business-products")
    private List<Product> products = new ArrayList<>();

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (active == null) {
            active = true;
        }
        if (verified == null) {
            verified = false;
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (isOpen == null) {
            isOpen = true;
        }
        if (paymentMode == null) {
            paymentMode = "NOT_CONFIGURED";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
