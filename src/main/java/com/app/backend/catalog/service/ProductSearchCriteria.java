package com.app.backend.catalog.service;

import java.math.BigDecimal;

public record ProductSearchCriteria(
        String category, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock, String name) {}
