package com.bizlink.repository;

import com.bizlink.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    List<Customer> findByBusinessId(UUID businessId);
    Optional<Customer> findByBusinessIdAndMobile(UUID businessId, String mobile);
}
