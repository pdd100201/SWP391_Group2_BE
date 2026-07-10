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

@Entity
@Table(name = "promotions")
public class Promotion extends BaseAuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "promotion_id")
    private Long id;

    private String code;


    @Enumerated(EnumType.STRING)


    @Column(name = "start_date", nullable = false)

    @Column(name = "end_date", nullable = false)

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;


}
