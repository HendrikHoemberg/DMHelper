package dev.hendrikhoemberg.dmhelper.gamemap.service;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

public record MapSettingsCommand(
        @JsonProperty(required = true) long expectedVersion,
        @JsonProperty(required = true) int gridWidth,
        @JsonProperty(required = true) int gridHeight,
        @JsonProperty(required = true) int cellSizePx,
        @JsonProperty(required = true) ResizeMode resizeMode,
        List<TokenResolution> tokenResolutions
) {
    public enum ResizeMode { PRESERVE, CROP }

    public record TokenResolution(
            @JsonProperty(required = true) UUID tokenId,
            @JsonProperty(required = true) TokenAction action,
            int positionX,
            int positionY
    ) {}

    public enum TokenAction { MOVE, REMOVE }
}
