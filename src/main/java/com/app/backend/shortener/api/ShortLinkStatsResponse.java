package com.app.backend.shortener.api;

import com.app.backend.shortener.domain.ShortLink;
import java.time.Instant;

public record ShortLinkStatsResponse(
        String code, String originalUrl, long visitCount, Instant createdAt, Instant expiresAt) {

    static ShortLinkStatsResponse from(ShortLink link) {
        return new ShortLinkStatsResponse(
                link.getCode(), link.getOriginalUrl(), link.getVisitCount(), link.getCreatedAt(), link.getExpiresAt());
    }
}
