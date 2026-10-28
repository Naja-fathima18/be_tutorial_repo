package com.app.backend.shortener.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShortenerProperties.class)
public class ShortenerConfiguration {}
