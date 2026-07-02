package com.swp391.api.modules.promotion.service;

import com.swp391.api.modules.promotion.dto.PromotionConditionsRequest;
import com.swp391.api.modules.promotion.dto.PromotionRequest;
import com.swp391.api.modules.promotion.dto.PromotionResponse;
import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.promotion.entity.PromotionType;
import com.swp391.api.modules.promotion.repository.PromotionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PromotionService {
    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> getAll(String search) {
        String keyword = normalize(search);
        return promotionRepository.findAll().stream()
                .filter(promotion -> keyword == null
                        || promotion.getCode().toLowerCase().contains(keyword.toLowerCase())
                        || promotion.getName().toLowerCase().contains(keyword.toLowerCase()))
                .sorted(Comparator.comparing(Promotion::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse getById(Long id) {
        return toResponse(findById(id));
    }

    public PromotionResponse create(PromotionRequest request) {
        String code = requireCode(request.getCode());
        if (promotionRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion code already exists");
        }
        Promotion promotion = new Promotion();
        applyRequest(promotion, request);
        promotion.setCode(code);
        return toResponse(promotionRepository.save(promotion));
    }

    public PromotionResponse update(Long id, PromotionRequest request) {
        Promotion promotion = findById(id);
        String code = requireCode(request.getCode());
        promotionRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion code already exists");
                });
        applyRequest(promotion, request);
        promotion.setCode(code);
        return toResponse(promotionRepository.save(promotion));
    }

    public PromotionResponse toggleStatus(Long id, boolean active) {
        Promotion promotion = findById(id);
        promotion.setIsActive(active);
        return toResponse(promotionRepository.save(promotion));
    }

    public void delete(Long id) {
        if (!promotionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found");
        }
        promotionRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PromotionConditionsRequest getConditions(Long id) {
        Promotion promotion = findById(id);
        PromotionConditionsRequest response = new PromotionConditionsRequest();
        response.setMinOrderAmount(promotion.getMinOrderAmount());
        response.setMaxDiscountAmount(promotion.getMaxDiscountAmount());
        response.setUsageLimit(promotion.getUsageLimit());
        return response;
    }

    public PromotionResponse updateConditions(Long id, PromotionConditionsRequest request) {
        Promotion promotion = findById(id);
        promotion.setMinOrderAmount(nonNegative(request.getMinOrderAmount(), BigDecimal.ZERO, "Minimum order amount"));
        promotion.setMaxDiscountAmount(nonNegativeOrNull(request.getMaxDiscountAmount(), "Maximum discount amount"));
        promotion.setUsageLimit(nonNegativeIntegerOrNull(request.getUsageLimit(), "Usage limit"));
        return toResponse(promotionRepository.save(promotion));
    }

    public PromotionApplication validateForInvoice(String code, BigDecimal subtotal) {
        Promotion promotion = promotionRepository.findByCodeIgnoreCase(requireCode(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion code not found"));
        validateAvailability(promotion, subtotal);
        return new PromotionApplication(promotion, calculateDiscount(promotion, subtotal));
    }

    public void increaseUsedCount(Promotion promotion) {
        if (promotion == null) return;
        promotion.setUsedCount((promotion.getUsedCount() == null ? 0 : promotion.getUsedCount()) + 1);
        promotionRepository.save(promotion);
    }

    private void applyRequest(Promotion promotion, PromotionRequest request) {
        promotion.setName(requireText(request.getName(), "Promotion name"));
        promotion.setType(request.getType());
        promotion.setValue(requiredPositive(request.getValue(), "Discount value"));
        promotion.setStartDate(request.getStartDate());
        promotion.setEndDate(request.getEndDate());
        promotion.setIsActive(request.getIsActive() == null || request.getIsActive());
        promotion.setMinOrderAmount(nonNegative(request.getMinOrderAmount(), BigDecimal.ZERO, "Minimum order amount"));
        promotion.setMaxDiscountAmount(nonNegativeOrNull(request.getMaxDiscountAmount(), "Maximum discount amount"));
        promotion.setUsageLimit(nonNegativeIntegerOrNull(request.getUsageLimit(), "Usage limit"));
        promotion.setDescription(normalize(request.getDescription()));
        validate(promotion);
    }

    private void validate(Promotion promotion) {
        if (promotion.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion type is required");
        }
        if (promotion.getStartDate() == null || promotion.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion period is required");
        }
        if (promotion.getEndDate().isBefore(promotion.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be on or after start date");
        }
        if (promotion.getType() == PromotionType.PERCENTAGE
                && promotion.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Percentage discount cannot exceed 100%");
        }
    }

    private void validateAvailability(Promotion promotion, BigDecimal subtotal) {
        if (!Boolean.TRUE.equals(promotion.getIsActive())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion is inactive");
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(promotion.getStartDate()) || today.isAfter(promotion.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion is outside its valid period");
        }
        BigDecimal minOrderAmount = promotion.getMinOrderAmount() == null ? BigDecimal.ZERO : promotion.getMinOrderAmount();
        if (subtotal.compareTo(minOrderAmount) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invoice does not meet the promotion minimum order amount");
        }
        Integer usageLimit = promotion.getUsageLimit();
        if (usageLimit != null && usageLimit > 0 && (promotion.getUsedCount() == null ? 0 : promotion.getUsedCount()) >= usageLimit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Promotion usage limit has been reached");
        }
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal subtotal) {
        BigDecimal discount = promotion.getType() == PromotionType.PERCENTAGE
                ? subtotal.multiply(promotion.getValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getValue();
        if (promotion.getMaxDiscountAmount() != null) {
            discount = discount.min(promotion.getMaxDiscountAmount());
        }
        return discount.min(subtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private Promotion findById(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found"));
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getName(),
                promotion.getType(),
                promotion.getValue(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getIsActive(),
                promotion.getMinOrderAmount(),
                promotion.getMaxDiscountAmount(),
                promotion.getUsageLimit(),
                promotion.getUsedCount(),
                promotion.getDescription(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt());
    }

    private String requireCode(String value) {
        String code = requireText(value, "Promotion code").toUpperCase();
        if (!code.matches("[A-Z0-9_-]{2,40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Promotion code may only contain letters, numbers, hyphen or underscore");
        }
        return code;
    }

    private String requireText(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return normalized;
    }

    private BigDecimal requiredPositive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must be greater than zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegative(BigDecimal value, BigDecimal fallback, String field) {
        if (value == null) return fallback.setScale(2, RoundingMode.HALF_UP);
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " cannot be negative");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nonNegativeOrNull(BigDecimal value, String field) {
        if (value == null) return null;
        return nonNegative(value, BigDecimal.ZERO, field);
    }

    private Integer nonNegativeIntegerOrNull(Integer value, String field) {
        if (value == null) return null;
        if (value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " cannot be negative");
        }
        return value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PromotionApplication(Promotion promotion, BigDecimal discountAmount) {
    }
}
