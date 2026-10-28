package com.app.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.user.domain.Role;
import com.app.backend.user.domain.User;
import com.app.backend.user.security.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private final JwtProperties properties =
            new JwtProperties("unit-test-signing-key-that-is-at-least-32-bytes", Duration.ofMinutes(15));

    @Test
    void issuesHs256TokenWithSubjectRolesAndFifteenMinuteExpiry() {
        TokenService tokenService = new TokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey())),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        User user = new User("alice@example.com", "$2a$10$hash", Role.ADMIN);
        ReflectionTestUtils.setField(user, "id", 7L);

        IssuedToken token = tokenService.issue(user);

        Jwt decoded = decoderWithoutTimestampCheck().decode(token.value());
        assertThat(token.ttl()).isEqualTo(Duration.ofSeconds(900));
        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getIssuedAt()).isEqualTo(NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
    }

    private NimbusJwtDecoder decoderWithoutTimestampCheck() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder;
    }
}
