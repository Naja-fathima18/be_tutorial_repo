package com.app.backend.shortener.api;

import com.app.backend.shortener.config.ShortenerProperties;
import com.app.backend.shortener.service.ShortLinkService;
import com.app.backend.shortener.service.ShortenResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
public class ShortLinkController {

    static final String CODE_PATTERN = "[A-Za-z0-9_-]{1,8}";

    private final ShortLinkService service;
    private final ShortenerProperties properties;

    public ShortLinkController(ShortLinkService service, ShortenerProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<ShortLinkResponse> shorten(@Valid @RequestBody ShortenUrlRequest request) {
        ShortenResult result = service.shorten(request.url(), request.expiresAt());
        String shortUrl = properties.shortUrlFor(result.link().getCode());
        ShortLinkResponse body = ShortLinkResponse.from(result.link(), shortUrl);
        if (result.created()) {
            return ResponseEntity.created(URI.create(shortUrl)).body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{code}/stats")
    public ShortLinkStatsResponse stats(@PathVariable @Pattern(regexp = CODE_PATTERN) String code) {
        return ShortLinkStatsResponse.from(service.getStats(code));
    }
}
