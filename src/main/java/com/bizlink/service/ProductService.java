package com.bizlink.service;

import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.model.Business;
import com.bizlink.model.Product;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.repository.ProductRepository;
import com.bizlink.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final BusinessRepository businessRepository;
    private final PlanLimitService planLimitService;
    private final FileStorageService fileStorage;

    public ProductService(
            ProductRepository productRepository,
            BusinessRepository businessRepository,
            PlanLimitService planLimitService,
            FileStorageService fileStorage) {
        this.productRepository = productRepository;
        this.businessRepository = businessRepository;
        this.planLimitService = planLimitService;
        this.fileStorage = fileStorage;
    }

    @Transactional
    public Product create(Product product, MultipartFile image) {
        log.info("Creating product for business: {}", product.getBusinessId());
        Business business = verifyBusinessAccess(product.getBusinessId());
        planLimitService.enforceProductLimit(product.getBusinessId());
        product.setBusiness(business);

        if (image != null && !image.isEmpty()) {
            product.setImageUrl(fileStorage.store(image, "products"));
        }

        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public List<Product> getByBusinessId(UUID businessId) {
        log.info("Fetching products for business: {}", businessId);
        verifyBusinessAccess(businessId);
        return productRepository.findByBusinessId(businessId);
    }

    @Transactional
    public Product update(UUID id, Product updates, MultipartFile image) {
        log.info("Updating product: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        verifyBusinessAccess(product.getBusinessId());

        if (updates.getName() != null) product.setName(updates.getName());
        if (updates.getDescription() != null) product.setDescription(updates.getDescription());
        if (updates.getOptions() != null) {
            product.setOptions(updates.getOptions().isBlank() ? null : updates.getOptions().trim());
        }
        if (updates.getPrice() != null) product.setPrice(updates.getPrice());
        if (updates.getCategoryId() != null) product.setCategoryId(updates.getCategoryId());
        if (updates.getAvailable() != null) product.setAvailable(updates.getAvailable());

        if (image != null && !image.isEmpty()) {
            product.setImageUrl(fileStorage.store(image, "products"));
        }

        return productRepository.save(product);
    }

    @Transactional
    public void delete(UUID id) {
        log.info("Deleting product: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        verifyBusinessAccess(product.getBusinessId());
        productRepository.delete(product);
    }

    private Business verifyBusinessAccess(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        User user = getCurrentUser();
        if (!business.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
            throw new UnauthorizedException("You do not own this business");
        }
        return business;
    }

    private User getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        throw new UnauthorizedException("Not authenticated");
    }
}
