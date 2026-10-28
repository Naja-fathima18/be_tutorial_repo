package com.app.backend.shortener.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.backend.shortener.domain.ShortLink;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class ShortLinkRepositoryTest {

    private static final String URL = "https://example.com/page";

    @Autowired
    private ShortLinkRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsLinkWithZeroVisitsAndCreationTimestamp() {
        ShortLink saved = repository.saveAndFlush(new ShortLink("Abc1234", URL, null));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVisitCount()).isZero();
        assertThat(repository.findByCode("Abc1234")).isPresent();
        assertThat(repository.existsByCode("Abc1234")).isTrue();
        assertThat(repository.existsByCode("Other00")).isFalse();
    }

    @Test
    void enforcesUniqueCode() {
        repository.saveAndFlush(new ShortLink("Abc1234", URL, null));

        assertThatThrownBy(() -> repository.saveAndFlush(new ShortLink("Abc1234", "https://other.example", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void incrementsVisitCountWithSingleUpdate() {
        repository.saveAndFlush(new ShortLink("Abc1234", URL, null));

        int first = repository.incrementVisitCount("Abc1234");
        int second = repository.incrementVisitCount("Abc1234");
        int unknown = repository.incrementVisitCount("Missing");
        entityManager.clear();

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(unknown).isZero();
        assertThat(repository.findByCode("Abc1234"))
                .get()
                .extracting(ShortLink::getVisitCount)
                .isEqualTo(2L);
    }

    @Test
    void findsReusableLinkByUrlWithoutExpiry() {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        repository.saveAndFlush(new ShortLink("WithExp", URL, expiresAt));
        repository.saveAndFlush(new ShortLink("NoExp01", URL, null));

        assertThat(repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(URL))
                .get()
                .extracting(ShortLink::getCode)
                .isEqualTo("NoExp01");
        assertThat(repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc("https://other.example"))
                .isEmpty();
    }

    @Test
    void findsReusableLinkByUrlAndExactExpiry() {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00.123456Z");
        repository.saveAndFlush(new ShortLink("NoExp01", URL, null));
        repository.saveAndFlush(new ShortLink("WithExp", URL, expiresAt));
        entityManager.clear();

        assertThat(repository.findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(URL, expiresAt))
                .get()
                .extracting(ShortLink::getCode)
                .isEqualTo("WithExp");
        assertThat(repository.findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(URL, expiresAt.plusSeconds(1)))
                .isEmpty();
    }
}
