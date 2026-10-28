package com.app.backend.order.api;

import com.app.backend.order.service.StockLevel;
import java.math.BigDecimal;

public record InventoryResponse(Long productId, String sku, String name, BigDecimal price, int stock) {

    static InventoryResponse from(StockLevel level) {
        return new InventoryResponse(level.productId(), level.sku(), level.name(), level.price(), level.stock());
    }
}
