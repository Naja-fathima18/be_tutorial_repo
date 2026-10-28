package com.app.backend.order.service;

import com.app.backend.order.domain.Order;
import com.app.backend.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetails(
        Long id,
        Long customerId,
        OrderStatus status,
        List<OrderLineDetails> items,
        BigDecimal total,
        Instant createdAt) {

    public OrderDetails {
        items = List.copyOf(items);
    }

    static OrderDetails from(Order order) {
        return new OrderDetails(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getItems().stream().map(OrderLineDetails::from).toList(),
                order.getTotal(),
                order.getCreatedAt());
    }
}
