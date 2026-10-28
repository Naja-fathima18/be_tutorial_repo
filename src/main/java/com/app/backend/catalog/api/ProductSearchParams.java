package com.app.backend.catalog.api;

import com.app.backend.catalog.service.ProductSearchCriteria;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductSearchParams(
        @Size(max = 100) String category,
        @DecimalMin("0.00") BigDecimal minPrice,
        @DecimalMin("0.00") BigDecimal maxPrice,
        Boolean inStock,
        @Size(max = 200) String name) {

    ProductSearchCriteria toCriteria() {
        return new ProductSearchCriteria(category, minPrice, maxPrice, inStock, name);
    }
}
