package com.swp391.api.modules.promotion.service;

import com.swp391.api.modules.promotion.dto.PromotionRequest;
import com.swp391.api.modules.promotion.dto.PromotionResponse;
import java.util.List;

public interface PromotionService {
    List<PromotionResponse> getAll();

    PromotionResponse getById(Long id);

    PromotionResponse create(PromotionRequest request);

    PromotionResponse update(Long id, PromotionRequest request);

    PromotionResponse updateStatus(Long id, boolean active);

    void delete(Long id);
}
