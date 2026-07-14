package com.bizlink.repository;

import com.bizlink.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByBusinessId(UUID businessId);
    Optional<Subscription> findFirstByBusinessIdAndStatusOrderByEndDateDesc(UUID businessId, String status);
    Optional<Subscription> findFirstByBusinessIdAndStatusAndPlanIdOrderByCreatedAtDesc(
            UUID businessId, String status, UUID planId);
    List<Subscription> findByStatusAndEndDateBefore(String status, LocalDate endDate);
    long countByStatus(String status);
    List<Subscription> findAllByOrderByCreatedAtDesc();
}
