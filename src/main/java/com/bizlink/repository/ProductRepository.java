package com.bizlink.repository;

import com.bizlink.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByBusinessId(UUID businessId);
    long countByBusinessId(UUID businessId);

    @Modifying
    @Query("UPDATE Product p SET p.categoryId = null WHERE p.categoryId = :categoryId")
    void clearCategoryId(@Param("categoryId") UUID categoryId);
}
