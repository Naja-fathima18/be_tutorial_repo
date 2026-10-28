package com.app.backend.order.domain;

import com.app.backend.common.error.ConflictException;

public class OrderNotCancellableException extends ConflictException {

    public OrderNotCancellableException(long orderId, OrderStatus status) {
        super("Order %d cannot be cancelled because it is %s".formatted(orderId, status));
    }
}
