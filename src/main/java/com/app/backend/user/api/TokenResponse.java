package com.app.backend.user.api;

import com.app.backend.user.service.IssuedToken;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static final String BEARER = "Bearer";

    static TokenResponse from(IssuedToken token) {
        return new TokenResponse(token.value(), BEARER, token.ttl().toSeconds());
    }

    @Override
    public String toString() {
        return "TokenResponse[accessToken=****, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
