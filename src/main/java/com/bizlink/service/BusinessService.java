package com.bizlink.service;

import com.bizlink.dto.RazorpayConnectRequest;
import com.bizlink.dto.RazorpayStatusDto;
import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.Business;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final PlanLimitService planLimitService;
    private final FileStorageService fileStorage;
    private final SecretCryptoService secretCryptoService;

    public BusinessService(
            BusinessRepository businessRepository,
            PlanLimitService planLimitService,
            FileStorageService fileStorage,
            SecretCryptoService secretCryptoService) {
        this.businessRepository = businessRepository;
        this.planLimitService = planLimitService;
        this.fileStorage = fileStorage;
        this.secretCryptoService = secretCryptoService;
    }

    @Value("${app.allow-platform-order-payments:true}")
    private boolean allowPlatformOrderPayments;

    @Transactional
    public Business create(Business business, MultipartFile logo, MultipartFile cover) {
        log.info("Creating business: {}", business.getBusinessName());
        User owner = getCurrentUser();

        if (business.getSlug() == null || business.getSlug().isBlank()) {
            business.setSlug(generateSlug(business.getBusinessName()));
        } else if (businessRepository.existsBySlug(business.getSlug())) {
            throw new ValidationException("Slug already exists");
        }

        business.setOwner(owner);
        business.setOwnerId(owner.getId());
        business.setPaymentMode("NOT_CONFIGURED");

        if (logo != null && !logo.isEmpty()) {
            business.setLogoUrl(fileStorage.store(logo, "logos"));
        }
        if (cover != null && !cover.isEmpty()) {
            business.setCoverImageUrl(fileStorage.store(cover, "covers"));
        }

        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public Business getById(UUID id) {
        log.info("Fetching business by id: {}", id);
        Business business = businessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        verifyOwner(business);
        business.getCategories().size();
        business.getProducts().size();
        return business;
    }

    @Transactional(readOnly = true)
    public Business getBySlug(String slug) {
        log.info("Fetching public business by slug: {}", slug);
        Business business = planLimitService.getPublicBusiness(slug);
        business.getCategories().size();
        business.getProducts().size();
        return business;
    }

    @Transactional(readOnly = true)
    public List<Business> getByOwner() {
        User owner = getCurrentUser();
        log.info("Fetching businesses for owner: {}", owner.getId());
        List<Business> businesses = businessRepository.findByOwnerId(owner.getId());
        businesses.forEach(b -> {
            b.getCategories().size();
            b.getProducts().size();
        });
        return businesses;
    }

    @Transactional
    public Business update(UUID id, Business updates, MultipartFile logo, MultipartFile cover) {
        log.info("Updating business: {}", id);
        Business business = getById(id);

        if (updates.getBusinessName() != null) business.setBusinessName(updates.getBusinessName());
        if (updates.getDescription() != null) business.setDescription(updates.getDescription());
        if (updates.getPhone() != null) business.setPhone(updates.getPhone());
        if (updates.getWhatsappNumber() != null) business.setWhatsappNumber(updates.getWhatsappNumber());
        if (updates.getAddress() != null) business.setAddress(updates.getAddress());
        if (updates.getCity() != null) business.setCity(updates.getCity());
        if (updates.getState() != null) business.setState(updates.getState());
        if (updates.getPincode() != null) business.setPincode(updates.getPincode());
        if (updates.getBusinessHours() != null) business.setBusinessHours(updates.getBusinessHours());
        if (updates.getIsOpen() != null) business.setIsOpen(updates.getIsOpen());
        if (updates.getLatitude() != null) business.setLatitude(updates.getLatitude());
        if (updates.getLongitude() != null) business.setLongitude(updates.getLongitude());
        if (updates.getActive() != null) business.setActive(updates.getActive());

        if (updates.getSlug() != null && !updates.getSlug().isBlank()) {
            String slug = updates.getSlug().trim().toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
            if (!slug.equals(business.getSlug()) && businessRepository.existsBySlug(slug)) {
                throw new ValidationException("This URL slug is already taken");
            }
            business.setSlug(slug);
        }

        if (logo != null && !logo.isEmpty()) {
            business.setLogoUrl(fileStorage.store(logo, "logos"));
        }
        if (cover != null && !cover.isEmpty()) {
            business.setCoverImageUrl(fileStorage.store(cover, "covers"));
        }

        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public RazorpayStatusDto getRazorpayStatus(UUID businessId) {
        Business business = getById(businessId);
        boolean configured = business.getRazorpayKeyId() != null && !business.getRazorpayKeyId().isBlank()
                && business.getRazorpayKeySecretEnc() != null && !business.getRazorpayKeySecretEnc().isBlank();
        return RazorpayStatusDto.builder()
                .paymentMode(business.getPaymentMode() != null ? business.getPaymentMode() : "NOT_CONFIGURED")
                .keyId(configured ? business.getRazorpayKeyId() : null)
                .configured(configured)
                .hasWebhookSecret(business.getRazorpayWebhookSecretEnc() != null
                        && !business.getRazorpayWebhookSecretEnc().isBlank())
                .platformOrderPaymentsAllowed(allowPlatformOrderPayments)
                .build();
    }

    @Transactional
    public RazorpayStatusDto connectRazorpay(UUID businessId, RazorpayConnectRequest request) {
        Business business = getById(businessId);
        String keyId = request.getKeyId().trim();
        String keySecret = request.getKeySecret().trim();
        if (!keyId.startsWith("rzp_")) {
            throw new ValidationException("Invalid Razorpay Key ID");
        }
        business.setRazorpayKeyId(keyId);
        business.setRazorpayKeySecretEnc(secretCryptoService.encrypt(keySecret));
        if (request.getWebhookSecret() != null && !request.getWebhookSecret().isBlank()) {
            business.setRazorpayWebhookSecretEnc(secretCryptoService.encrypt(request.getWebhookSecret().trim()));
        }
        business.setPaymentMode("MERCHANT_DIRECT");
        businessRepository.save(business);
        log.info("Razorpay connected for business {}", businessId);
        return getRazorpayStatus(businessId);
    }

    @Transactional
    public RazorpayStatusDto disconnectRazorpay(UUID businessId) {
        Business business = getById(businessId);
        business.setRazorpayKeyId(null);
        business.setRazorpayKeySecretEnc(null);
        business.setRazorpayWebhookSecretEnc(null);
        business.setPaymentMode("NOT_CONFIGURED");
        businessRepository.save(business);
        log.info("Razorpay disconnected for business {}", businessId);
        return getRazorpayStatus(businessId);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting business: {}", id);
        Business business = getById(id);
        businessRepository.delete(business);
    }

    private void verifyOwner(Business business) {
        User owner = getCurrentUser();
        if (!business.getOwnerId().equals(owner.getId()) && !"ADMIN".equals(owner.getRole())) {
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

    private String generateSlug(String name) {
        String base = name.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "business";
        }
        String slug = base;
        int counter = 1;
        while (businessRepository.existsBySlug(slug)) {
            slug = base + "-" + counter++;
        }
        return slug;
    }
}
