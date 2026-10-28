package com.app.backend.catalog.api;

import com.app.backend.catalog.service.ProductCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String category,

        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @NotNull @PositiveOrZero Integer stock,

        @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1)
        BigDecimal rating) {

    ProductCommand toCommand() {
        return new ProductCommand(name.strip(), category.strip(), price, stock, rating);
    }
}
