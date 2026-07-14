package com.bizlink.repository;

import com.bizlink.model.BusinessQr;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BusinessQrRepository extends JpaRepository<BusinessQr, UUID> {

    Optional<BusinessQr> findFirstByBusinessIdAndLabelIsNull(UUID businessId);
}
