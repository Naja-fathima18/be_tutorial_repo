package com.app.backend.shortener.api;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record ShortenUrlRequest(
        @NotBlank @Size(max = 2048) @HttpUrl String url, @Future Instant expiresAt) {}
