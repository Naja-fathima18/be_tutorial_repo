package com.app.backend.order.domain;

import com.app.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException() {
        super(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Idempotency-Key was already used with a different request payload");
    }
}
