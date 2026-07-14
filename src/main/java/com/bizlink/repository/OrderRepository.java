package com.bizlink.repository;

import com.bizlink.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByBusinessIdOrderByCreatedAtDesc(UUID businessId);

    List<Order> findByBusinessIdAndCreatedAtBefore(UUID businessId, LocalDateTime createdAt);

    List<Order> findByBusinessIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            UUID businessId, LocalDateTime createdAt);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderNumber = :orderNumber")
    Optional<Order> findByOrderNumberWithItems(@Param("orderNumber") String orderNumber);

    long countByBusinessId(UUID businessId);

    long countByBusinessIdAndCreatedAtGreaterThanEqual(UUID businessId, LocalDateTime createdAt);

    long countByBusinessIdAndStatusIgnoreCase(UUID businessId, String status);

    long countByBusinessIdAndStatusIgnoreCaseAndCreatedAtGreaterThanEqual(
            UUID businessId, String status, LocalDateTime createdAt);

    long countByBusinessIdAndCustomerId(UUID businessId, UUID customerId);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.businessId = :businessId AND UPPER(o.paymentStatus) = 'PAID'
            """)
    BigDecimal sumPaidRevenue(@Param("businessId") UUID businessId);

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o
            WHERE o.businessId = :businessId AND UPPER(o.paymentStatus) = 'PAID'
              AND o.createdAt >= :from
            """)
    BigDecimal sumPaidRevenueSince(@Param("businessId") UUID businessId, @Param("from") LocalDateTime from);
}
