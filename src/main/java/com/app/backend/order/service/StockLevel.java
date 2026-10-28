package com.app.backend.order.service;

import com.app.backend.order.domain.InventoryItem;
import java.math.BigDecimal;

public record StockLevel(Long productId, String sku, String name, BigDecimal price, int stock) {

    static StockLevel from(InventoryItem item) {
        return new StockLevel(item.getId(), item.getSku(), item.getName(), item.getPrice(), item.getStock());
    }
}
