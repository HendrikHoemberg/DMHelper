package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import org.springframework.core.io.InputStreamSource;

public record HandoutImportSource(
        String originalDisplayName,
        String contentType,
        InputStreamSource content,
        long expectedSize,
        String expectedSha256
) {}
