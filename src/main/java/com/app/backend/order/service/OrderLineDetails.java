package com.app.backend.order.service;

import com.app.backend.order.domain.OrderItem;
import java.math.BigDecimal;

public record OrderLineDetails(
        Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {

    static OrderLineDetails from(OrderItem item) {
        return new OrderLineDetails(
                item.getProductId(),
                item.getProductName(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal());
    }
}
