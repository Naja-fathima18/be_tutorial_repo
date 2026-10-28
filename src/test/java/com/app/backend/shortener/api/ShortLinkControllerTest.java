package com.app.backend.shortener.api;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.app.backend.common.error.NotFoundException;
import com.app.backend.shortener.config.ShortenerConfiguration;
import com.app.backend.shortener.domain.ShortLink;
import com.app.backend.shortener.service.ShortLinkExpiredException;
import com.app.backend.shortener.service.ShortLinkService;
import com.app.backend.shortener.service.ShortenResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ShortLinkController.class, RedirectController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(ShortenerConfiguration.class)
@TestPropertySource(properties = "app.shortener.base-url=https://sho.rt/")
class ShortLinkControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShortLinkService service;

    private static ShortLink link(String code, String url, Instant expiresAt, long visits) {
        ShortLink link = new ShortLink(code, url, expiresAt);
        ReflectionTestUtils.setField(link, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(link, "visitCount", visits);
        return link;
    }

    private static String body(String url) {
        return """
                {"url":"%s"}
                """.formatted(url);
    }

    @Test
    void createsShortLinkWithLocationAndShortUrl() throws Exception {
        given(service.shorten(eq("https://example.com/a?b=c"), isNull()))
                .willReturn(new ShortenResult(link("Ab3dE9x", "https://example.com/a?b=c", null, 0), true));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com/a?b=c")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "https://sho.rt/Ab3dE9x"))
                .andExpect(jsonPath("$.code").value("Ab3dE9x"))
                .andExpect(jsonPath("$.shortUrl").value("https://sho.rt/Ab3dE9x"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com/a?b=c"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").isEmpty());
    }

    @Test
    void returnsExistingLinkWithOkAndNoLocationWhenAlreadyShortened() throws Exception {
        given(service.shorten(anyString(), any()))
                .willReturn(new ShortenResult(link("Ab3dE9x", "https://example.com", null, 4), false));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("https://example.com")))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.code").value("Ab3dE9x"));
    }

    @Test
    void passesExpiryToService() throws Exception {
        Instant expiresAt = Instant.parse("2099-01-01T00:00:00Z");
        given(service.shorten("https://example.com", expiresAt))
                .willReturn(new ShortenResult(link("Ab3dE9x", "https://example.com", expiresAt, 0), true));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value("2099-01-01T00:00:00Z"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "example.com",
                "/relative/path",
                "ftp://example.com/file",
                "javascript:alert(1)",
                "mailto:someone@example.com",
                "http://",
                "http:///path",
                "https://exa mple.com"
            })
    void rejectsInvalidUrlWithFieldError(String url) throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("url")));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void rejectsMissingUrl() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"));
    }

    @Test
    void rejectsUrlLongerThan2048Characters() throws Exception {
        String url = "https://example.com/" + "a".repeat(2029);

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("url"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 0 and 2048"));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void acceptsUrlOfExactly2048Characters() throws Exception {
        String url = "https://example.com/" + "a".repeat(2028);
        given(service.shorten(eq(url), isNull())).willReturn(new ShortenResult(link("Ab3dE9x", url, null, 0), true));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(url)))
                .andExpect(status().isCreated());
    }

    @Test
    void acceptsUppercaseHttpScheme() throws Exception {
        given(service.shorten(eq("HTTP://example.com"), isNull()))
                .willReturn(new ShortenResult(link("Ab3dE9x", "HTTP://example.com", null, 0), true));

        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("HTTP://example.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsExpiryInThePast() throws Exception {
        mockMvc.perform(post("/api/urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("expiresAt"));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void redirectsWithFoundToOriginalUrl() throws Exception {
        given(service.resolveAndCountVisit("Ab3dE9x")).willReturn("https://example.com/a?b=c");

        mockMvc.perform(get("/Ab3dE9x"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/a?b=c"));
    }

    @Test
    void returnsNotFoundProblemForUnknownCode() throws Exception {
        given(service.resolveAndCountVisit("unknown")).willThrow(new NotFoundException("Short link not found"));

        mockMvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsGoneProblemForExpiredCode() throws Exception {
        given(service.resolveAndCountVisit("expired")).willThrow(new ShortLinkExpiredException("expired"));

        mockMvc.perform(get("/expired"))
                .andExpect(status().isGone())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.status").value(410))
                .andExpect(jsonPath("$.detail").value("Short link 'expired' has expired"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/toolongcode", "/bad.code", "/api/urls", "/actuator/health"})
    void doesNotRouteNonCodePathsToRedirect(String path) throws Exception {
        mockMvc.perform(get(path));

        then(service).shouldHaveNoInteractions();
    }

    @Test
    void returnsStatsForCode() throws Exception {
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        given(service.getStats("Ab3dE9x")).willReturn(link("Ab3dE9x", "https://example.com", expiresAt, 42));

        mockMvc.perform(get("/api/urls/Ab3dE9x/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("Ab3dE9x"))
                .andExpect(jsonPath("$.originalUrl").value("https://example.com"))
                .andExpect(jsonPath("$.visitCount").value(42))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
                .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"));
    }

    @Test
    void returnsNotFoundForStatsOfUnknownCode() throws Exception {
        given(service.getStats("unknown")).willThrow(new NotFoundException("Short link not found"));

        mockMvc.perform(get("/api/urls/unknown/stats")).andExpect(status().isNotFound());
    }

    @Test
    void rejectsStatsForCodeOutsideAlphabet() throws Exception {
        mockMvc.perform(get("/api/urls/waytoolongcode/stats"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("code"));

        then(service).shouldHaveNoInteractions();
    }
}
