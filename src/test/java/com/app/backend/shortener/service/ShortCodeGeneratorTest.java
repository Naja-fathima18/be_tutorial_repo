package com.app.backend.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesSevenCharacterBase62Codes() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.next()).hasSize(7).matches("[A-Za-z0-9]{7}");
        }
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();

        for (int i = 0; i < 1_000; i++) {
            codes.add(generator.next());
        }

        assertThat(codes).hasSize(1_000);
    }
}
