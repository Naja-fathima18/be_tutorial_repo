package com.app.backend.user.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void failsFastWhenSecretIsMissing() {
        runner.run(context ->
                assertThat(context).hasFailed().getFailure().rootCause().hasMessageContaining("JWT_SECRET"));
    }

    @Test
    void failsFastWhenSecretIsShorterThanThirtyTwoBytes() {
        runner.withPropertyValues("app.security.jwt.secret=" + "x".repeat(31))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .hasMessageContaining("at least 32 bytes"));
    }

    @Test
    void defaultsTtlToFifteenMinutes() {
        runner.withPropertyValues("app.security.jwt.secret=" + "x".repeat(32))
                .run(context ->
                        assertThat(context.getBean(JwtProperties.class).ttl()).isEqualTo(Duration.ofMinutes(15)));
    }

    @Test
    void doesNotExposeSecretInToString() {
        JwtProperties properties = new JwtProperties("y".repeat(40), Duration.ofMinutes(15));

        assertThat(properties.toString()).doesNotContain("yyyy");
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfiguration {}
}
