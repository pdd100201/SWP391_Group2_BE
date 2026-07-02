package com.swp391.api.modules.user.entity;

import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * Lớp cơ sở (base class) cung cấp tính năng audit tự động cho các entity.
 *
 * <p>{@code @MappedSuperclass}: Class này không có bảng riêng trong DB,
 * nhưng các field của nó sẽ được ánh xạ vào bảng của các subclass.
 * Mọi entity kế thừa class này đều có thêm cột {@code created_at} và {@code updated_at}.</p>
 *
 * <p>{@code @EntityListeners(AuditingEntityListener.class)}: Gắn listener của Spring Data JPA
 * để tự động điền giá trị thời gian khi entity được tạo hoặc cập nhật.
 * Yêu cầu {@code @EnableJpaAuditing} được bật trong {@code AuditingConfig}.</p>
 *
 * <p>Lợi ích: Không cần viết code set thời gian thủ công ở mỗi entity,
 * giảm thiểu lỗi và đảm bảo nhất quán dữ liệu audit.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseAuditableEntity {

    /**
     * Thời điểm entity được tạo lần đầu.
     * {@code @CreatedDate}: Spring Data tự động điền khi entity được persist lần đầu.
     * {@code updatable = false}: Không cho phép cập nhật sau khi đã tạo, đảm bảo tính toàn vẹn.
     */
    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * Thời điểm entity được cập nhật lần cuối.
     * {@code @LastModifiedDate}: Spring Data tự động cập nhật mỗi khi entity được save().
     * Giúp theo dõi ai/khi nào thay đổi dữ liệu.
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Lấy thời điểm entity được tạo.
     *
     * @return Thời điểm tạo, null nếu entity chưa được persist
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Set thời điểm tạo (thường không gọi thủ công - JPA Auditing tự làm).
     * Chỉ dùng trong trường hợp đặc biệt như import dữ liệu cũ.
     *
     * @param createdAt Thời điểm tạo cần set
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Lấy thời điểm entity được cập nhật lần cuối.
     *
     * @return Thời điểm cập nhật gần nhất
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Set thời điểm cập nhật (thường không gọi thủ công - JPA Auditing tự làm).
     *
     * @param updatedAt Thời điểm cập nhật cần set
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
