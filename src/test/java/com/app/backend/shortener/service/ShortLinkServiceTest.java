package com.app.backend.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.shortener.domain.ShortLink;
import com.app.backend.shortener.persistence.ShortLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
    private static final String URL = "https://example.com/page";

    @Mock
    private ShortLinkRepository repository;

    @Mock
    private ShortCodeGenerator codeGenerator;

    @Mock
    private ObjectProvider<Clock> clockProvider;

    private ShortLinkService service;

    @BeforeEach
    void setUp() {
        given(clockProvider.getIfUnique(any())).willReturn(Clock.fixed(NOW, ZoneOffset.UTC));
        service = new ShortLinkService(repository, codeGenerator, clockProvider);
    }

    @Test
    void createsNewLinkWithGeneratedCodeWhenUrlNotShortenedYet() {
        given(repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(URL))
                .willReturn(Optional.empty());
        given(codeGenerator.next()).willReturn("Abc1234");
        given(repository.existsByCode("Abc1234")).willReturn(false);
        given(repository.saveAndFlush(any(ShortLink.class))).willAnswer(invocation -> invocation.getArgument(0));

        ShortenResult result = service.shorten(URL, null);

        assertThat(result.created()).isTrue();
        assertThat(result.link().getCode()).isEqualTo("Abc1234");
        assertThat(result.link().getOriginalUrl()).isEqualTo(URL);
        assertThat(result.link().getExpiresAt()).isNull();
    }

    @Test
    void returnsExistingLinkForSameUrlWithoutExpiry() {
        ShortLink existing = new ShortLink("Exist01", URL, null);
        given(repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(URL))
                .willReturn(Optional.of(existing));

        ShortenResult result = service.shorten(URL, null);

        assertThat(result.created()).isFalse();
        assertThat(result.link()).isSameAs(existing);
        then(repository).should(never()).saveAndFlush(any());
        then(codeGenerator).shouldHaveNoInteractions();
    }

    @Test
    void returnsExistingLinkForSameUrlAndSameExpiry() {
        Instant expiresAt = NOW.plusSeconds(3600);
        ShortLink existing = new ShortLink("Exist01", URL, expiresAt);
        given(repository.findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(URL, expiresAt))
                .willReturn(Optional.of(existing));

        ShortenResult result = service.shorten(URL, expiresAt);

        assertThat(result.created()).isFalse();
        assertThat(result.link()).isSameAs(existing);
        then(repository).should(never()).saveAndFlush(any());
    }

    @Test
    void createsNewLinkWhenMatchingLinkHasAlreadyExpired() {
        Instant expiresAt = NOW.minusSeconds(1);
        given(repository.findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(URL, expiresAt))
                .willReturn(Optional.of(new ShortLink("Old0001", URL, expiresAt)));
        given(codeGenerator.next()).willReturn("New0001");
        given(repository.saveAndFlush(any(ShortLink.class))).willAnswer(invocation -> invocation.getArgument(0));

        ShortenResult result = service.shorten(URL, expiresAt);

        assertThat(result.created()).isTrue();
        assertThat(result.link().getCode()).isEqualTo("New0001");
    }

    @Test
    void normalizesExpiryToMicrosecondsSoRepeatedRequestsMatchStoredValue() {
        Instant withNanos = Instant.parse("2026-07-01T00:00:00.123456789Z");
        Instant truncated = Instant.parse("2026-07-01T00:00:00.123456Z");
        given(repository.findFirstByOriginalUrlAndExpiresAtOrderByIdAsc(URL, truncated))
                .willReturn(Optional.empty());
        given(codeGenerator.next()).willReturn("Abc1234");
        given(repository.saveAndFlush(any(ShortLink.class))).willAnswer(invocation -> invocation.getArgument(0));

        ShortenResult result = service.shorten(URL, withNanos);

        assertThat(result.link().getExpiresAt()).isEqualTo(truncated);
    }

    @Test
    void retriesCodeGenerationOnCollision() {
        given(repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(URL))
                .willReturn(Optional.empty());
        given(codeGenerator.next()).willReturn("Taken01", "Taken02", "Free001");
        given(repository.existsByCode("Taken01")).willReturn(true);
        given(repository.existsByCode("Taken02")).willReturn(true);
        given(repository.existsByCode("Free001")).willReturn(false);
        given(repository.saveAndFlush(any(ShortLink.class))).willAnswer(invocation -> invocation.getArgument(0));

        ShortenResult result = service.shorten(URL, null);

        ArgumentCaptor<ShortLink> saved = ArgumentCaptor.forClass(ShortLink.class);
        then(repository).should().saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("Free001");
        assertThat(result.link().getCode()).isEqualTo("Free001");
    }

    @Test
    void failsAfterBoundedNumberOfCollisions() {
        given(repository.findFirstByOriginalUrlAndExpiresAtIsNullOrderByIdAsc(URL))
                .willReturn(Optional.empty());
        given(codeGenerator.next()).willReturn("Taken01");
        given(repository.existsByCode(anyString())).willReturn(true);

        assertThatThrownBy(() -> service.shorten(URL, null))
                .isInstanceOf(ShortCodeExhaustedException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        then(codeGenerator).should(times(ShortLinkService.MAX_CODE_ATTEMPTS)).next();
        then(repository).should(never()).saveAndFlush(any());
    }

    @Test
    void resolvesActiveLinkAndIncrementsVisitCountAtomically() {
        given(repository.findByCode("Abc1234")).willReturn(Optional.of(new ShortLink("Abc1234", URL, null)));

        String target = service.resolveAndCountVisit("Abc1234");

        assertThat(target).isEqualTo(URL);
        then(repository).should().incrementVisitCount("Abc1234");
    }

    @Test
    void resolvesLinkThatExpiresInTheFuture() {
        given(repository.findByCode("Abc1234"))
                .willReturn(Optional.of(new ShortLink("Abc1234", URL, NOW.plusSeconds(1))));

        String target = service.resolveAndCountVisit("Abc1234");

        assertThat(target).isEqualTo(URL);
    }

    @Test
    void rejectsUnknownCodeWithNotFound() {
        given(repository.findByCode("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveAndCountVisit("missing")).isInstanceOf(NotFoundException.class);

        then(repository).should(never()).incrementVisitCount(anyString());
    }

    @Test
    void rejectsLinkExpiringExactlyNowWithGoneAndDoesNotCount() {
        given(repository.findByCode("Abc1234")).willReturn(Optional.of(new ShortLink("Abc1234", URL, NOW)));

        assertThatThrownBy(() -> service.resolveAndCountVisit("Abc1234"))
                .isInstanceOf(ShortLinkExpiredException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.GONE);

        then(repository).should(never()).incrementVisitCount(anyString());
    }

    @Test
    void returnsStatsForKnownCode() {
        ShortLink link = new ShortLink("Abc1234", URL, null);
        given(repository.findByCode("Abc1234")).willReturn(Optional.of(link));

        ShortLink stats = service.getStats("Abc1234");

        assertThat(stats).isSameAs(link);
    }

    @Test
    void rejectsStatsForUnknownCode() {
        given(repository.findByCode("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStats("missing")).isInstanceOf(NotFoundException.class);
    }
}
