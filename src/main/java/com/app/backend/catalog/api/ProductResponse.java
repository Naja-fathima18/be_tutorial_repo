package com.app.backend.catalog.api;

import com.app.backend.catalog.service.ProductView;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock,
        BigDecimal rating,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static ProductResponse from(ProductView view) {
        return new ProductResponse(
                view.id(),
                view.name(),
                view.category(),
                view.price(),
                view.stock(),
                view.rating(),
                view.createdAt(),
                view.updatedAt(),
                view.version());
    }
}
