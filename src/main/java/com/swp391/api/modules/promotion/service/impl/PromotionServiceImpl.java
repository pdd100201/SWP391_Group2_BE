package com.swp391.api.modules.promotion.service.impl;

import com.swp391.api.common.exception.BusinessException;
import com.swp391.api.modules.account.dto.PageResponse;
import com.swp391.api.modules.promotion.dto.PromotionRequest;
import com.swp391.api.modules.promotion.dto.PromotionResponse;
import com.swp391.api.modules.promotion.entity.DiscountType;
import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.promotion.entity.PromotionStatus;
import com.swp391.api.modules.promotion.repository.PromotionRepository;
import com.swp391.api.modules.promotion.service.PromotionService;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromotionServiceImpl implements PromotionService {

    //BigDecimal so sánh bằng compareTo, không dùng dấu >.
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PromotionRepository promotionRepository;

    public PromotionServiceImpl(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getAll() {
        return promotionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPage(int page, int size, String search, String status) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String keyword = trimToNull(search);
        Boolean active = parseStatusFilter(status);
        Page<Promotion> result = promotionRepository.searchPromotions(
                keyword,
                active,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return new PageResponse<>(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getById(Long id) {
        return toResponse(findPromotion(id));
    }

    @Override
    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        validateRequest(request);

        String code = normalizeCode(request.code());
        if (promotionRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Promotion code already exists.");
        }

        Promotion promotion = new Promotion();
        applyRequest(promotion, request, code);
        promotion.setUsedCount(0);
        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public PromotionResponse update(Long id, PromotionRequest request) {
        validateRequest(request);

        Promotion promotion = findPromotion(id);
        String code = normalizeCode(request.code());
        if (promotionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Promotion code already exists.");
        }

        applyRequest(promotion, request, code);
        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public PromotionResponse updateStatus(Long id, boolean active) {
        Promotion promotion = findPromotion(id);
        promotion.setStatus(active ? PromotionStatus.ACTIVE : PromotionStatus.INACTIVE);
        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Promotion promotion = findPromotion(id);
        if (promotionRepository.countOrdersUsingPromotion(id) > 0) {
            throw new BusinessException("This promotion is already used by orders. Deactivate it instead of deleting.");
        }
        promotionRepository.delete(promotion);
    }

    private Promotion findPromotion(Long id) {
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found."));
    }

    private void applyRequest(Promotion promotion, PromotionRequest request, String code) {
        promotion.setCode(code);
        promotion.setPromotionName(request.name().trim());
        promotion.setDescription(trimToNull(request.description()));
        promotion.setDiscountType(request.type());
        promotion.setDiscountValue(request.value());
        promotion.setMinOrderAmount(defaultZero(request.minOrderAmount()));
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());
        promotion.setUsageLimit(request.usageLimit());
        promotion.setStatus(request.status() == null ? PromotionStatus.ACTIVE : request.status());
    }

    private void validateRequest(PromotionRequest request) {
        if (request.value() == null || request.value().compareTo(ZERO) <= 0) {
            throw new BusinessException("Discount value must be greater than 0.");
        }
        if (request.type() == DiscountType.PERCENT && request.value().compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException("Percent discount cannot be greater than 100.");
        }
        if (request.minOrderAmount() != null && request.minOrderAmount().compareTo(ZERO) < 0) {
            throw new BusinessException("Minimum order amount cannot be negative.");
        }
        if (request.maxDiscountAmount() != null && request.maxDiscountAmount().compareTo(ZERO) <= 0) {
            throw new BusinessException("Maximum discount amount must be greater than 0.");
        }
        if (request.usageLimit() != null && request.usageLimit() <= 0) {
            throw new BusinessException("Usage limit must be greater than 0.");
        }
        if (request.startDate() != null && request.endDate() != null && !request.endDate().isAfter(request.startDate())) {
            throw new BusinessException("End date must be after start date.");
        }
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getPromotionName(),
                promotion.getDescription(),
                promotion.getDiscountType().name(),
                promotion.getDiscountValue(),
                promotion.getMinOrderAmount(),
                promotion.getMaxDiscountAmount(),
                promotion.getStartDate(),
                promotion.getEndDate(),
                promotion.getUsageLimit(),
                promotion.getUsedCount(),
                promotion.getStatus().name(),
                promotion.getStatus() == PromotionStatus.ACTIVE,
                promotion.getCreatedAt(),
                promotion.getUpdatedAt()
        );
    }
    //Xóa khoảng trắng đầu/cuối và chuyển code thành chữ hoa.
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Boolean parseStatusFilter(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return Boolean.TRUE;
        }
        if ("INACTIVE".equalsIgnoreCase(status)) {
            return Boolean.FALSE;
        }
        throw new BusinessException("Invalid promotion status filter.");
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? ZERO : value;
    }
}
