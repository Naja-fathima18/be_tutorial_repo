package com.app.backend.catalog.service;

import java.math.BigDecimal;

public record ProductCommand(String name, String category, BigDecimal price, int stock, BigDecimal rating) {}
