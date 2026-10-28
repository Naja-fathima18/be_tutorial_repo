package com.app.backend.order.api;

import com.app.backend.order.service.OrderLineDetails;
import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {

    static OrderItemResponse from(OrderLineDetails line) {
        return new OrderItemResponse(
                line.productId(), line.productName(), line.unitPrice(), line.quantity(), line.lineTotal());
    }
}
