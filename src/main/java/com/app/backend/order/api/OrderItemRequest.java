package com.app.backend.order.api;

import com.app.backend.order.service.PlaceOrderCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(1) @Max(OrderItemRequest.MAX_QUANTITY) Integer quantity) {

    static final int MAX_QUANTITY = 1000;

    PlaceOrderCommand.Line toLine() {
        return new PlaceOrderCommand.Line(productId, quantity);
    }
}
