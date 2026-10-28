package com.app.backend.order.api;

import com.app.backend.order.service.PlaceOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PlaceOrderRequest(
        @NotNull @Positive Long customerId,
        @NotEmpty @Size(max = PlaceOrderRequest.MAX_ITEMS) List<@NotNull @Valid OrderItemRequest> items) {

    static final int MAX_ITEMS = 50;

    PlaceOrderCommand toCommand() {
        return new PlaceOrderCommand(
                customerId, items.stream().map(OrderItemRequest::toLine).toList());
    }
}
