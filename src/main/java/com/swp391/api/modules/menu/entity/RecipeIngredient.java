package com.swp391.api.modules.menu.entity;

import com.swp391.api.modules.inventory.entity.InventoryItem;
import jakarta.persistence.*;

@Entity
@Table(
        name = "inventory_linked_recipe_ingredients",
        uniqueConstraints = @UniqueConstraint(columnNames = {"menu_item_id", "inventory_item_id"})
)
// Join entity defining how much inventory one menu item consumes per serving.
public class RecipeIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @Column(name = "required_quantity", nullable = false)
    private Double requiredQuantity;

    public Long getId() { return id; }
    public MenuItem getMenuItem() { return menuItem; }
    public void setMenuItem(MenuItem menuItem) { this.menuItem = menuItem; }
    public InventoryItem getInventoryItem() { return inventoryItem; }
    public void setInventoryItem(InventoryItem inventoryItem) { this.inventoryItem = inventoryItem; }
    public Double getRequiredQuantity() { return requiredQuantity; }
    public void setRequiredQuantity(Double requiredQuantity) { this.requiredQuantity = requiredQuantity; }
}
