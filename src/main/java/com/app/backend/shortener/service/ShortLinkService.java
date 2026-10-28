package com.app.backend.shortener.service;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.shortener.domain.ShortLink;
import com.app.backend.shortener.persistence.ShortLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortLinkService {

    static final int MAX_CODE_ATTEMPTS = 5;

    private static final Logger log = LoggerFactory.getLogger(ShortLinkService.class);

    private final ShortLinkRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final Clock clock;

    public ShortLinkService(
            ShortLinkRepository repository, ShortCodeGenerator codeGenerator, ObjectProvider<Clock> clockProvider) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.clock = clockProvider.getIfUnique(Clock::systemUTC);
    }

    @Transactional
    public ShortenResult shorten(String originalUrl, Instant expiresAt) {
        Instant normalizedExpiry = expiresAt == null ? null : expiresAt.truncatedTo(ChronoUnit.MICROS);
        Optional<ShortLink> existing = findReusable(originalUrl, normalizedExpiry);
        if (existing.isPresent()) {
            return new ShortenResult(existing.get(), false);
        }
        ShortLink saved = repository.saveAndFlush(new ShortLink(uniqueCode(), originalUrl, normalizedExpiry));
        log.info("Created short link {}", saved.getCode());
        return new ShortenResult(saved, true);
    }

    @Transactional
    public String resolveAndCountVisit(String code) {
        ShortLink link = findByCode(code);
        if (link.isExpiredAt(clock.instant())) {
            throw new ShortLinkExpiredException(code);
        }
        repository.incrementVisitCount(code);
        return link.getOriginalUrl();
    }

    @Transactional(readOnly = true)
    public ShortLink getStats(String code) {
        return findByCode(code);
    }

    private ShortLink findByCode(String code) {
        return repository
                .findByCode(code)
                .orElseThrow(() -> new NotFoundException("Short link '" + code + "' not found"));
    }

    private Optional<ShortLink> findReusable(String originalUrl, Instant expiresAt) {
        Optional<ShortLink> candidate = expiresAt == null
                ? repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(originalUrl)
                : repository.findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(originalUrl, expiresAt);
        Instant now = clock.instant();
        return candidate.filter(link -> !link.isExpiredAt(now));
    }

    private String uniqueCode() {
        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.next();
            if (!repository.existsByCode(code)) {
                return code;
            }
            log.warn("Short code collision on attempt {}", attempt);
        }
        throw new ShortCodeExhaustedException(MAX_CODE_ATTEMPTS);
    }
}
