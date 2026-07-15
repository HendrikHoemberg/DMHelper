package dev.hendrikhoemberg.dmhelper.campaign.packagev2.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetDescriptor(
        String key,
        String path,
        String mediaType,
        long sizeBytes,
        String sha256,
        String originalName
) {}
