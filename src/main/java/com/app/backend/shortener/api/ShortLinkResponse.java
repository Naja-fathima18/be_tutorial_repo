package com.app.backend.shortener.api;

import com.app.backend.shortener.domain.ShortLink;
import java.time.Instant;

public record ShortLinkResponse(
        String code, String shortUrl, String originalUrl, Instant expiresAt, Instant createdAt) {

    static ShortLinkResponse from(ShortLink link, String shortUrl) {
        return new ShortLinkResponse(
                link.getCode(), shortUrl, link.getOriginalUrl(), link.getExpiresAt(), link.getCreatedAt());
    }
}
