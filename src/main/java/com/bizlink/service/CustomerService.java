package com.bizlink.service;

import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.ValidationException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.model.Business;
import com.bizlink.model.Customer;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.CustomerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final BusinessRepository businessRepository;

    public CustomerService(CustomerRepository customerRepository, BusinessRepository businessRepository) {
        this.customerRepository = customerRepository;
        this.businessRepository = businessRepository;
    }

    @Transactional
    public Customer create(Customer customer) {
        log.info("Creating customer for business: {}", customer.getBusinessId());

        if (customer.getBusinessId() == null) {
            throw new ValidationException("Business is required");
        }
        if (customer.getName() == null || customer.getName().isBlank()) {
            throw new ValidationException("Customer name is required");
        }
        if (customer.getName().length() > 150) {
            throw new ValidationException("Name is too long");
        }
        String mobile = normalizeMobile(customer.getMobile());
        if (mobile == null) {
            throw new ValidationException("Valid 10-digit mobile number is required");
        }
        customer.setMobile(mobile);

        if (!businessRepository.existsById(customer.getBusinessId())) {
            throw new ResourceNotFoundException("Business not found");
        }

        Business business = businessRepository.findById(customer.getBusinessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        if (Boolean.FALSE.equals(business.getActive())) {
            throw new ResourceNotFoundException("Business not found");
        }
        if (business.getStatus() != null && !"ACTIVE".equalsIgnoreCase(business.getStatus())) {
            throw new ResourceNotFoundException("Business not found");
        }

        return customerRepository
                .findByBusinessIdAndMobile(customer.getBusinessId(), customer.getMobile())
                .map(existing -> {
                    existing.setName(customer.getName());
                    if (customer.getEmail() != null) {
                        existing.setEmail(customer.getEmail());
                    }
                    return customerRepository.save(existing);
                })
                .orElseGet(() -> customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<Customer> getByBusinessId(UUID businessId) {
        log.info("Fetching customers for business: {}", businessId);
        verifyBusinessAccess(businessId);
        return customerRepository.findByBusinessId(businessId);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting customer: {}", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        verifyBusinessAccess(customer.getBusinessId());
        customerRepository.delete(customer);
    }

    private void verifyBusinessAccess(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        User user = getCurrentUser();
        if (!business.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new UnauthorizedException("You do not own this business");
        }
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new UnauthorizedException("Not authenticated");
    }

    private String normalizeMobile(String mobile) {
        if (mobile == null) return null;
        String digits = mobile.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10) return null;
        return digits;
    }
}
