package com.swp391.api.modules.promotion.service;

import com.swp391.api.modules.promotion.dto.PromotionRequest;
import com.swp391.api.modules.promotion.dto.PromotionResponse;
import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.promotion.repository.PromotionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromotionService {
    private final PromotionRepository promotionRepository;

    public PromotionService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Transactional(readOnly = true)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse getById(Long id) {
    }

    public PromotionResponse create(PromotionRequest request) {
        if (promotionRepository.existsByCodeIgnoreCase(code)) {
        }
        Promotion promotion = new Promotion();
        return toResponse(promotionRepository.save(promotion));
    }

    public PromotionResponse update(Long id, PromotionRequest request) {
        return toResponse(promotionRepository.save(promotion));
    }

        return toResponse(promotionRepository.save(promotion));
    }

    public void delete(Long id) {
    }
    }

        return promotionRepository.findById(id)
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getCode(),
                promotion.getMinOrderAmount(),
                promotion.getMaxDiscountAmount(),
                promotion.getUsageLimit(),
                promotion.getUsedCount(),
                promotion.getCreatedAt(),
    }

    }

    }
    }

    }
}
