package com.bizlink.repository;

import com.bizlink.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    List<Payment> findByBusinessId(UUID businessId);
    Optional<Payment> findByTransactionId(String transactionId);
    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, String status);
    List<Payment> findByOrderIdAndStatus(UUID orderId, String status);
    List<Payment> findByOrderIdIn(Collection<UUID> orderIds);
    List<Payment> findAllByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCESS'")
    BigDecimal sumSuccessfulPayments();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'SUCCESS' AND p.subscriptionId IS NOT NULL")
    BigDecimal sumSubscriptionRevenue();

    @Query(value = """
            SELECT TO_CHAR(created_at, 'YYYY-MM') AS month, COALESCE(SUM(amount), 0)
            FROM payments WHERE status = 'SUCCESS'
            GROUP BY TO_CHAR(created_at, 'YYYY-MM')
            ORDER BY month
            """, nativeQuery = true)
    List<Object[]> monthlyRevenue();
}
