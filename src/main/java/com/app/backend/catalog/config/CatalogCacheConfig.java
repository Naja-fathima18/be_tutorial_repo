package com.app.backend.catalog.config;

import com.app.backend.catalog.service.ProductService;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@EnableCaching(order = CatalogCacheConfig.CACHE_ADVICE_OUTSIDE_TRANSACTIONS)
@EnableConfigurationProperties(CatalogCacheProperties.class)
public class CatalogCacheConfig {

    static final int CACHE_ADVICE_OUTSIDE_TRANSACTIONS = Ordered.LOWEST_PRECEDENCE - 1;

    @Bean
    CacheManagerCustomizer<CaffeineCacheManager> productsCacheCustomizer(CatalogCacheProperties properties) {
        return cacheManager -> cacheManager.registerCustomCache(
                ProductService.PRODUCTS_CACHE,
                Caffeine.newBuilder()
                        .expireAfterWrite(properties.ttl())
                        .maximumSize(properties.maxSize())
                        .recordStats()
                        .build());
    }
}
