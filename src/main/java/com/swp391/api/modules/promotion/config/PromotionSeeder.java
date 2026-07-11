package com.swp391.api.modules.promotion.config;

import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.promotion.entity.PromotionType;
import com.swp391.api.modules.promotion.repository.PromotionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
public class PromotionSeeder implements CommandLineRunner {
    private final PromotionRepository promotionRepository;

    public PromotionSeeder(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Override
    public void run(String... args) {
//        if (promotionRepository.count() > 0) return;
//        promotionRepository.save(build(
//                "WELCOME10",
//                "Welcome discount",
//                PromotionType.PERCENTAGE,
//                BigDecimal.valueOf(10),
//                BigDecimal.valueOf(100000),
//                BigDecimal.valueOf(50000)));
//        promotionRepository.save(build(
//                "DINNER50K",
//                "Dinner voucher",
//                PromotionType.FIXED_AMOUNT,
//                BigDecimal.valueOf(50000),
//                BigDecimal.valueOf(300000),
//                null));
    }

//    private Promotion build(
//            String code,
//            String name,
//            PromotionType type,
//            BigDecimal value,
//            BigDecimal minOrderAmount,
//            BigDecimal maxDiscountAmount) {
//        Promotion promotion = new Promotion();
//        promotion.setCode(code);
//        promotion.setName(name);
//        promotion.setType(type);
//        promotion.setValue(value.setScale(2));
//        promotion.setStartDate(LocalDate.now().minusDays(30));
//        promotion.setEndDate(LocalDate.now().plusMonths(6));
//        promotion.setIsActive(true);
//        promotion.setMinOrderAmount(minOrderAmount.setScale(2));
//        promotion.setMaxDiscountAmount(maxDiscountAmount == null ? null : maxDiscountAmount.setScale(2));
//        promotion.setUsedCount(0);
//        promotion.setDescription("Sample promotion for invoice payment testing");
//        return promotion;
//    }
}
