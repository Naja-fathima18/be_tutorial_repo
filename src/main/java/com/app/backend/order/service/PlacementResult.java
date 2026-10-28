package com.app.backend.order.service;

public record PlacementResult(OrderDetails order, boolean created) {

    static PlacementResult created(OrderDetails order) {
        return new PlacementResult(order, true);
    }

    static PlacementResult replayed(OrderDetails order) {
        return new PlacementResult(order, false);
    }
}
