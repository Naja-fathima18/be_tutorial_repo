package com.app.backend.shortener.service;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ShortLinkExpiredException extends ApiException {

    public ShortLinkExpiredException(String code) {
        super(HttpStatus.GONE, "Short link '" + code + "' has expired");
    }
}
