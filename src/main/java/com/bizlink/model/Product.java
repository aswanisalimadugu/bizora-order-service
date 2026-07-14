package com.bizlink.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    @JsonBackReference("business-products")
    private Business business;

    @Column(name = "business_id", insertable = false, updatable = false)
    private UUID businessId;

    @Column(name = "category_id")
    private UUID categoryId;

    @NotBlank
    @Size(max = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Comma-separated customization options, e.g. "Spicy, Double masala". */
    @Column(columnDefinition = "TEXT")
    private String options;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @NotNull
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private Boolean available = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (available == null) {
            available = true;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
