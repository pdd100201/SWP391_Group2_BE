package com.swp391.api.modules.qr.dto;

import java.util.List;

public class QrMenuResponse {

    private List<CategoryDto> categories;

    public QrMenuResponse() {}

    public QrMenuResponse(List<CategoryDto> categories) {
        this.categories = categories;
    }

    public List<CategoryDto> getCategories() { return categories; }
    public void setCategories(List<CategoryDto> categories) { this.categories = categories; }

    public static class CategoryDto {
        private Long categoryId;
        private String categoryName;
        private List<ItemDto> items;

        public CategoryDto() {}

        public CategoryDto(Long categoryId, String categoryName, List<ItemDto> items) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.items = items;
        }

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

        public String getCategoryName() { return categoryName; }
        public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

        public List<ItemDto> getItems() { return items; }
        public void setItems(List<ItemDto> items) { this.items = items; }
    }

    public static class ItemDto {
        private Long itemId;
        private String itemName;
        private Double price;
        private String imageUrl;
        private String description;

        public ItemDto() {}

        public ItemDto(Long itemId, String itemName, Double price, String imageUrl, String description) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.price = price;
            this.imageUrl = imageUrl;
            this.description = description;
        }

        public Long getItemId() { return itemId; }
        public void setItemId(Long itemId) { this.itemId = itemId; }

        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }

        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

    }
}
