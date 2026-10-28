package com.app.backend.order.service;

public record StoredOrder(String requestHash, OrderDetails details) {}
