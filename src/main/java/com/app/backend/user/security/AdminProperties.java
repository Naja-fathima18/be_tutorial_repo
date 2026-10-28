package com.app.backend.user.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties("app.security.admin")
public record AdminProperties(String email, String password) {

    public boolean isConfigured() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }

    @Override
    public String toString() {
        return "AdminProperties[email=****, password=****]";
    }
}
