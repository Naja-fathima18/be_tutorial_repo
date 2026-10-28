package com.app.backend.shortener.service;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ShortCodeExhaustedException extends ApiException {

    public ShortCodeExhaustedException(int attempts) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Could not allocate a unique short code after " + attempts + " attempts");
    }
}
