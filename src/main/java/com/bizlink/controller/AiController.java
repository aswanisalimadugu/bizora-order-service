package com.bizlink.controller;

import com.bizlink.exception.ResourceNotFoundException;
import com.bizlink.exception.UnauthorizedException;
import com.bizlink.exception.ValidationException;
import com.bizlink.model.Business;
import com.bizlink.model.User;
import com.bizlink.repository.BusinessRepository;
import com.bizlink.response.ApiResponse;
import com.bizlink.service.AiService;
import com.bizlink.service.PlanLimitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final PlanLimitService planLimitService;
    private final BusinessRepository businessRepository;

    public AiController(
            AiService aiService,
            PlanLimitService planLimitService,
            BusinessRepository businessRepository) {
        this.aiService = aiService;
        this.planLimitService = planLimitService;
        this.businessRepository = businessRepository;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<Map<String, String>>> generate(@RequestBody Map<String, Object> body) {
        assertAiAllowed(body);
        String type = (String) body.get("type");
        if (type == null || type.isBlank()) {
            throw new ValidationException("Field 'type' is required");
        }
        log.info("POST /api/ai/generate type={}", type);
        @SuppressWarnings("unchecked")
        Map<String, Object> params = body.get("params") instanceof Map
                ? (Map<String, Object>) body.get("params")
                : Map.of();
        String text = aiService.generate(type, params);
        return ResponseEntity.ok(ApiResponse.success("Generated", Map.of("text", text)));
    }

    @PostMapping("/command")
    public ResponseEntity<ApiResponse<Map<String, Object>>> command(@RequestBody Map<String, Object> body) {
        assertAiAllowed(body);
        String prompt = body.get("prompt") instanceof String ? (String) body.get("prompt") : null;
        if (prompt == null || prompt.isBlank()) {
            throw new ValidationException("Field 'prompt' is required");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> context = body.get("context") instanceof Map
                ? (Map<String, Object>) body.get("context")
                : Map.of();
        log.info("POST /api/ai/command page={}", context.get("page"));
        Map<String, Object> result = aiService.processCommand(prompt, context);
        return ResponseEntity.ok(ApiResponse.success("OK", result));
    }

    private void assertAiAllowed(Map<String, Object> body) {
        UUID businessId = extractBusinessId(body);
        if (businessId == null) {
            throw new ValidationException("businessId is required for AI features");
        }
        verifyBusinessAccess(businessId);
        planLimitService.assertPaidFeature(businessId, "AI assistant");
    }

    private void verifyBusinessAccess(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            if (!business.getOwnerId().equals(user.getId()) && !"ADMIN".equals(user.getRole())) {
                throw new UnauthorizedException("You do not own this business");
            }
            return;
        }
        throw new UnauthorizedException("Not authenticated");
    }

    private UUID extractBusinessId(Map<String, Object> body) {
        Object direct = body.get("businessId");
        UUID fromDirect = parseUuid(direct);
        if (fromDirect != null) {
            return fromDirect;
        }
        if (body.get("params") instanceof Map<?, ?> params) {
            UUID fromParams = parseUuid(params.get("businessId"));
            if (fromParams != null) {
                return fromParams;
            }
        }
        if (body.get("context") instanceof Map<?, ?> context) {
            return parseUuid(context.get("businessId"));
        }
        return null;
    }

    private UUID parseUuid(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
