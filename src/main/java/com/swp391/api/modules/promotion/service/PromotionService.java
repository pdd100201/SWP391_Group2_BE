package com.swp391.api.modules.promotion.service;

import com.swp391.api.modules.promotion.dto.PromotionRequest;
import com.swp391.api.modules.promotion.dto.PromotionResponse;
import com.swp391.api.modules.account.dto.PageResponse;
import java.util.List;

public interface PromotionService {
    List<PromotionResponse> getAll();

    PageResponse<PromotionResponse> getPage(int page, int size, String search, String status);

    PromotionResponse getById(Long id);

    PromotionResponse create(PromotionRequest request);

    PromotionResponse update(Long id, PromotionRequest request);

    PromotionResponse updateStatus(Long id, boolean active);

    void delete(Long id);
}
