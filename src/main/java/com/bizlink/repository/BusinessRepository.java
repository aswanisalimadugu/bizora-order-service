package com.bizlink.repository;

import com.bizlink.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessRepository extends JpaRepository<Business, UUID> {
    Optional<Business> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<Business> findByOwnerId(UUID ownerId);
    long countByStatus(String status);
    long countByActiveTrue();
}
