package com.app.backend.shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.shortener")
public record ShortenerProperties(
        @DefaultValue("http://localhost:8080") String baseUrl) {

    public String shortUrlFor(String code) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/" + code;
    }
}
