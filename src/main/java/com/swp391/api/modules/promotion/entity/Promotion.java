package com.swp391.api.modules.promotion.entity;

import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "promotions")
public class Promotion extends BaseAuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PromotionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "max_discount_amount", precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    @Column(length = 500)
    private String description;

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPromotionName() { return name; }
    public void setPromotionName(String promotionName) { this.name = promotionName; }
    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }
    public DiscountType getDiscountType() {
        return type == PromotionType.PERCENTAGE ? DiscountType.PERCENT : DiscountType.FIXED;
    }
    public void setDiscountType(DiscountType discountType) {
        this.type = discountType == DiscountType.PERCENT ? PromotionType.PERCENTAGE : PromotionType.FIXED_AMOUNT;
    }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
    public BigDecimal getDiscountValue() { return value; }
    public void setDiscountValue(BigDecimal discountValue) { this.value = discountValue; }
    public LocalDateTime getStartDate() { return startDate; }
    public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
    public LocalDateTime getEndDate() { return endDate; }
    public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
    public PromotionStatus getStatus() {
        return Boolean.TRUE.equals(isActive) ? PromotionStatus.ACTIVE : PromotionStatus.INACTIVE;
    }
    public void setStatus(PromotionStatus status) {
        this.isActive = status == PromotionStatus.ACTIVE;
    }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public Integer getUsedCount() { return usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
