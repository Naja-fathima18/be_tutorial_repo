package com.app.backend.catalog.service;

import com.app.backend.catalog.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductView(
        Long id,
        String name,
        String category,
        BigDecimal price,
        int stock,
        BigDecimal rating,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    static ProductView from(Product product) {
        return new ProductView(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStock(),
                product.getRating(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getVersion());
    }
}
