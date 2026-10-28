package com.app.backend.catalog.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.catalog.cache")
public record CatalogCacheProperties(
        @NotNull Duration ttl, @Positive long maxSize) {}
