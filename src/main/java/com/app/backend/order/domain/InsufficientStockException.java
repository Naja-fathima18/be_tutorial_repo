package com.app.backend.order.domain;

import com.app.backend.common.error.ConflictException;

public class InsufficientStockException extends ConflictException {

    public InsufficientStockException(long productId, String productName, int requested, int available) {
        super("Insufficient stock for product %d (%s): requested %d, available %d"
                .formatted(productId, productName, requested, available));
    }
}
