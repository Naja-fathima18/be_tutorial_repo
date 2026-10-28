package com.app.backend.user.service;

import java.time.Duration;

public record IssuedToken(String value, Duration ttl) {

    @Override
    public String toString() {
        return "IssuedToken[value=****, ttl=" + ttl + "]";
    }
}
