package com.app.backend.order.api;

import com.app.backend.order.domain.OrderStatus;
import com.app.backend.order.service.OrderDetails;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        List<OrderItemResponse> items,
        BigDecimal total,
        Instant createdAt) {

    static OrderResponse from(OrderDetails order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.status(),
                order.items().stream().map(OrderItemResponse::from).toList(),
                order.total(),
                order.createdAt());
    }
}
