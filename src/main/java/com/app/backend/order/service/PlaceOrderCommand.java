package com.app.backend.order.service;

import java.util.List;

public record PlaceOrderCommand(Long customerId, List<Line> lines) {

    public PlaceOrderCommand {
        lines = List.copyOf(lines);
    }

    public record Line(Long productId, int quantity) {}
}
