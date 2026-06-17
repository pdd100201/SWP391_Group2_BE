package com.swp391.api.modules.menu.entity;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory_linked_menu_reservation_ingredients")
public class MenuReservationIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private MenuReservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "inventory_item_name", nullable = false)
    private String inventoryItemName;

    @Column(nullable = false)
    private String unit;

    @Column(name = "required_quantity", nullable = false)
    private Double requiredQuantity;

    public MenuReservation getReservation() { return reservation; }
    public void setReservation(MenuReservation reservation) { this.reservation = reservation; }
    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }
    public String getInventoryItemName() { return inventoryItemName; }
    public void setInventoryItemName(String inventoryItemName) { this.inventoryItemName = inventoryItemName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Double getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(Double requiredQuantity) { this.requiredQuantity = requiredQuantity; }
}
