package com.bizlink.repository;

import com.bizlink.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findByBusinessId(UUID businessId);
    long countByBusinessId(UUID businessId);
    boolean existsByBusinessIdAndNameIgnoreCase(UUID businessId, String name);
    boolean existsByBusinessIdAndNameIgnoreCaseAndIdNot(UUID businessId, String name, UUID id);
}
