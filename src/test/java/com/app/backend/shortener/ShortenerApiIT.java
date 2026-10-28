package com.app.backend.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.backend.shortener.domain.ShortLink;
import com.app.backend.shortener.persistence.ShortLinkRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShortenerApiIT {

    private static final int PARALLEL_VISITS = 100;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ShortLinkRepository repository;

    private final HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @BeforeEach
    @AfterEach
    void cleanLinks() {
        repository.deleteAll();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> postJson(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String shorten(String url) throws Exception {
        HttpResponse<String> response = postJson("/api/urls", "{\"url\":\"" + url + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body()).get("code").asText();
    }

    private long visitCount(String code) throws Exception {
        HttpResponse<String> stats = get("/api/urls/" + code + "/stats");
        assertThat(stats.statusCode()).isEqualTo(200);
        return objectMapper.readTree(stats.body()).get("visitCount").asLong();
    }

    @Test
    void shortensRedirectsAndReportsStats() throws Exception {
        HttpResponse<String> created = postJson("/api/urls", "{\"url\":\"https://example.com/docs?page=1\"}");
        JsonNode body = objectMapper.readTree(created.body());
        String code = body.get("code").asText();

        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(code).hasSizeLessThanOrEqualTo(8).matches("[A-Za-z0-9_-]+");
        assertThat(body.get("shortUrl").asText()).isEqualTo("http://localhost:8080/" + code);
        assertThat(created.headers().firstValue("Location")).contains("http://localhost:8080/" + code);

        HttpResponse<String> redirect = get("/" + code);

        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).contains("https://example.com/docs?page=1");

        JsonNode stats =
                objectMapper.readTree(get("/api/urls/" + code + "/stats").body());

        assertThat(stats.get("code").asText()).isEqualTo(code);
        assertThat(stats.get("originalUrl").asText()).isEqualTo("https://example.com/docs?page=1");
        assertThat(stats.get("visitCount").asLong()).isEqualTo(1);
        assertThat(stats.get("createdAt").isNull()).isFalse();
    }

    @Test
    void returnsSameLinkWhenSameUrlIsShortenedTwice() throws Exception {
        String firstCode = shorten("https://example.com/same");

        HttpResponse<String> second = postJson("/api/urls", "{\"url\":\"https://example.com/same\"}");

        assertThat(second.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(second.body()).get("code").asText()).isEqualTo(firstCode);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createsSeparateLinkWhenExpiryDiffers() throws Exception {
        String withoutExpiry = shorten("https://example.com/expiry");

        HttpResponse<String> withExpiry = postJson(
                "/api/urls", "{\"url\":\"https://example.com/expiry\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");
        HttpResponse<String> sameExpiryAgain = postJson(
                "/api/urls", "{\"url\":\"https://example.com/expiry\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}");

        String expiringCode =
                objectMapper.readTree(withExpiry.body()).get("code").asText();
        assertThat(withExpiry.statusCode()).isEqualTo(201);
        assertThat(expiringCode).isNotEqualTo(withoutExpiry);
        assertThat(sameExpiryAgain.statusCode()).isEqualTo(200);
        assertThat(objectMapper.readTree(sameExpiryAgain.body()).get("code").asText())
                .isEqualTo(expiringCode);
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void returnsNotFoundProblemForUnknownCode() throws Exception {
        HttpResponse<String> response = get("/Zz9Zz9Z");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(objectMapper.readTree(response.body()).get("status").asInt()).isEqualTo(404);
    }

    @Test
    void returnsGoneProblemForExpiredCodeWithoutCountingVisit() throws Exception {
        ShortLink expired = new ShortLink(
                "Expired", "https://example.com/old", Instant.now().minusSeconds(60));
        repository.saveAndFlush(expired);

        HttpResponse<String> response = get("/Expired");

        assertThat(response.statusCode()).isEqualTo(410);
        assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
        assertThat(objectMapper.readTree(response.body()).get("status").asInt()).isEqualTo(410);
        assertThat(visitCount("Expired")).isZero();
    }

    @Test
    void rejectsInvalidUrlWithFieldError() throws Exception {
        HttpResponse<String> response = postJson("/api/urls", "{\"url\":\"ftp://example.com\"}");

        JsonNode body = objectMapper.readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(body.get("errors").get(0).get("field").asText()).isEqualTo("url");
    }

    @Test
    void actuatorHealthIsNotShadowedByRedirect() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void countsEveryParallelVisitExactlyOnce() throws Exception {
        String code = shorten("https://example.com/popular");
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(32);

        try {
            for (int i = 0; i < PARALLEL_VISITS; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return get("/" + code).statusCode();
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(302);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(visitCount(code)).isEqualTo(PARALLEL_VISITS);
    }
}
