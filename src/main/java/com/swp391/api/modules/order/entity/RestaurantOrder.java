package com.swp391.api.modules.order.entity;

import com.swp391.api.modules.reservation.entity.Reservation;
import com.swp391.api.modules.promotion.entity.Promotion;
import com.swp391.api.modules.user.entity.BaseAuditableEntity;
import com.swp391.api.modules.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "restaurant_orders")
// Aggregate gốc của một đơn bàn; liên kết reservation, waiter, món và token QR công khai.
public class RestaurantOrder extends BaseAuditableEntity {
    // Khóa chính nội bộ; orderCode là mã nghiệp vụ hiển thị cho nhân viên.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    // Một reservation có thể có nhiều RestaurantOrder, mỗi bàn một order.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    // Lưu ID bàn trực tiếp để giữ quan hệ order-bàn ổn định trong suốt phiên phục vụ.
    @Column(name = "table_id", nullable = false)
    private Long tableId;

    // Nhân viên chịu trách nhiệm được chốt tại thời điểm tạo order.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "waiter_id", nullable = false)
    private User waiter;

    // Token ngẫu nhiên dùng trong URL QR; unique để mỗi đường dẫn chỉ mở đúng một order.
    @Column(name = "public_access_token", nullable = false, unique = true, length = 64)
    private String publicAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.OPEN;

    // Ghi chú chung của toàn order, khác với ghi chú riêng của từng món.
    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    // Số tiền giảm được lưu để tương thích luồng promotion theo order cũ.
    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    // Optimistic locking ngăn một lần lưu cũ âm thầm ghi đè thay đổi mới hơn.
    @Version
    private Long version;

    // cascade lưu món theo order; orphanRemoval xóa DB khi món DRAFT bị bỏ khỏi danh sách.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<OrderItem> items = new ArrayList<>();

    // Thiết lập đồng thời hai phía quan hệ để JPA lưu đúng khóa ngoại order_id.
    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }
    public Long getTableId() { return tableId; }
    public void setTableId(Long tableId) { this.tableId = tableId; }
    public User getWaiter() { return waiter; }
    public void setWaiter(User waiter) { this.waiter = waiter; }
    public String getPublicAccessToken() { return publicAccessToken; }
    public void setPublicAccessToken(String publicAccessToken) { this.publicAccessToken = publicAccessToken; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Promotion getPromotion() { return promotion; }
    public void setPromotion(Promotion promotion) { this.promotion = promotion; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public Long getVersion() { return version; }
    public List<OrderItem> getItems() { return items; }
}
