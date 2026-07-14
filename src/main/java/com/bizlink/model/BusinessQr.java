package com.bizlink.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "business_qr")
@Getter
@Setter
@NoArgsConstructor
public class BusinessQr {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    @Column(length = 100)
    private String label;

    @Column(name = "qr_image_path", nullable = false, length = 500)
    private String qrImagePath;

    @Column(name = "scan_url", nullable = false, length = 500)
    private String scanUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
