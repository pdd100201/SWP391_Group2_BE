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

    // Hang so dung khi validate so tien/gia tri giam gia.
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    // Repository dung de thao tac voi bang promotions.
    private final PromotionRepository promotionRepository;

    public PromotionServiceImpl(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getAll() {
        // Lay tat ca promotion, sap xep promotion moi tao len dau.
        return promotionRepository.findAllByOrderByCreatedAtDesc().stream()
                // Doi entity Promotion sang DTO PromotionResponse de tra ve frontend.
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PromotionResponse> getPage(int page, int size, String search, String status) {
        // Dam bao page khong bi am.
        int safePage = Math.max(page, 0);

        // Dam bao size toi thieu la 1, toi da la 100.
        int safeSize = Math.min(Math.max(size, 1), 100);

        // Search rong thi doi thanh null de repository bo qua dieu kien search.
        String keyword = trimToNull(search);

        // Doi status filter tu ALL/ACTIVE/INACTIVE sang Boolean.
        Boolean active = parseStatusFilter(status);

        // Goi repository search co phan trang, sap xep moi nhat truoc.
        Page<Promotion> result = promotionRepository.searchPromotions(
                keyword,
                active,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        // Doi Page<Promotion> thanh PageResponse<PromotionResponse>.
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
        // Tim promotion theo id roi doi sang response.
        return toResponse(findPromotion(id));
    }

    @Override
    @Transactional
    public PromotionResponse create(PromotionRequest request) {
        // Validate cac rule co ban truoc khi tao promotion.
        validateRequest(request);

        // Chuan hoa code: trim va viet hoa.
        String code = normalizeCode(request.code());

        // Khong cho tao trung ma promotion.
        if (promotionRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Promotion code already exists.");
        }

        // Tao entity promotion moi.
        Promotion promotion = new Promotion();

        // Gan data request vao entity.
        applyRequest(promotion, request, code);

        // Promotion moi chua ai dung nen usedCount = 0.
        promotion.setUsedCount(0);

        // Luu DB roi tra response.
        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public PromotionResponse update(Long id, PromotionRequest request) {
        // Validate request truoc khi update.
        validateRequest(request);

        // Tim promotion can update, khong co thi 404.
        Promotion promotion = findPromotion(id);

        // Chuan hoa code moi.
        String code = normalizeCode(request.code());

        // Neu code moi bi trung voi promotion khac thi bao loi.
        if (promotionRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Promotion code already exists.");
        }

        // Gan data moi vao entity dang co.
        applyRequest(promotion, request, code);

        // Save voi entity da co id thi la update.
        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public PromotionResponse updateStatus(Long id, boolean active) {
        // Tim promotion can doi status.
        Promotion promotion = findPromotion(id);

        // active = true thi ACTIVE, false thi INACTIVE.
        promotion.setStatus(active ? PromotionStatus.ACTIVE : PromotionStatus.INACTIVE);

        // Luu status moi vao DB.
        return toResponse(promotionRepository.save(promotion));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        // Tim promotion can xoa.
        Promotion promotion = findPromotion(id);

        // Neu promotion da duoc order su dung thi khong cho xoa that.
        if (promotionRepository.countOrdersUsingPromotion(id) > 0) {
            throw new BusinessException("This promotion is already used by orders. Deactivate it instead of deleting.");
        }

        // Chua order nao dung thi duoc xoa khoi DB.
        promotionRepository.delete(promotion);
    }

    private Promotion findPromotion(Long id) {
        // Tim promotion theo id; khong thay thi nem 404.
        return promotionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Promotion not found."));
    }

    private void applyRequest(Promotion promotion, PromotionRequest request, String code) {
        // Gan ma promotion da duoc chuan hoa.
        promotion.setCode(code);

        // Gan ten promotion va bo khoang trang dau/cuoi.
        promotion.setPromotionName(request.name().trim());

        // Description rong thi luu null.
        promotion.setDescription(trimToNull(request.description()));

        // Gan loai giam gia: PERCENT hoac FIXED.
        promotion.setDiscountType(request.type());

        // Gan gia tri giam gia.
        promotion.setDiscountValue(request.value());

        // Neu minOrderAmount null thi mac dinh la 0.
        promotion.setMinOrderAmount(defaultZero(request.minOrderAmount()));

        // Gan muc giam toi da neu co.
        promotion.setMaxDiscountAmount(request.maxDiscountAmount());

        // Gan thoi gian bat dau/ket thuc.
        promotion.setStartDate(request.startDate());
        promotion.setEndDate(request.endDate());

        // Gan gioi han so lan su dung neu co.
        promotion.setUsageLimit(request.usageLimit());

        // Neu request khong gui status thi mac dinh ACTIVE.
        promotion.setStatus(request.status() == null ? PromotionStatus.ACTIVE : request.status());
    }

    private void validateRequest(PromotionRequest request) {
        // Gia tri giam gia bat buoc phai > 0.
        if (request.value() == null || request.value().compareTo(ZERO) <= 0) {
            throw new BusinessException("Discount value must be greater than 0.");
        }

        // Neu giam theo phan tram thi khong duoc vuot qua 100%.
        if (request.type() == DiscountType.PERCENT && request.value().compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessException("Percent discount cannot be greater than 100.");
        }

        // Hoa don toi thieu khong duoc am.
        if (request.minOrderAmount() != null && request.minOrderAmount().compareTo(ZERO) < 0) {
            throw new BusinessException("Minimum order amount cannot be negative.");
        }

        // Muc giam toi da neu co thi phai > 0.
        if (request.maxDiscountAmount() != null && request.maxDiscountAmount().compareTo(ZERO) <= 0) {
            throw new BusinessException("Maximum discount amount must be greater than 0.");
        }

        // Gioi han so lan dung neu co thi phai > 0.
        if (request.usageLimit() != null && request.usageLimit() <= 0) {
            throw new BusinessException("Usage limit must be greater than 0.");
        }

        // Ngay ket thuc phai sau ngay bat dau.
        if (request.startDate() != null && request.endDate() != null && !request.endDate().isAfter(request.startDate())) {
            throw new BusinessException("End date must be after start date.");
        }
    }

    private PromotionResponse toResponse(Promotion promotion) {
        // Doi entity Promotion thanh DTO tra ve frontend.
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

    // Bo khoang trang dau/cuoi va chuyen code thanh chu hoa.
    private String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        // Neu value null hoac toan khoang trang thi tra null.
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        // Nguoc lai tra ve chuoi da trim.
        return value.trim();
    }

    private Boolean parseStatusFilter(String status) {
        // ALL/null/rong nghia la khong loc theo status.
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }

        // ACTIVE duoc map thanh true.
        if ("ACTIVE".equalsIgnoreCase(status)) {
            return Boolean.TRUE;
        }

        // INACTIVE duoc map thanh false.
        if ("INACTIVE".equalsIgnoreCase(status)) {
            return Boolean.FALSE;
        }

        // Status filter khong hop le thi bao loi.
        throw new BusinessException("Invalid promotion status filter.");
    }

    private BigDecimal defaultZero(BigDecimal value) {
        // Neu null thi dung 0, nguoc lai giu nguyen value.
        return value == null ? ZERO : value;
    }
}
