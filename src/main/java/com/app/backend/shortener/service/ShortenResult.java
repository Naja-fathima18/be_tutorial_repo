package com.app.backend.shortener.service;

import com.app.backend.shortener.domain.ShortLink;

public record ShortenResult(ShortLink link, boolean created) {}
