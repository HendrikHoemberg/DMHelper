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
        List<TokenResolution> tokenResolutions,
        List<ShapeRemoval> shapeRemovals,
        List<UUID> tokenRestoreIds,
        List<TokenSnapshot> tokenSnapshot,
        List<TokenSnapshot> expectedTokenSnapshot,
        MapDocumentDto document
) {
    public MapSettingsCommand(long expectedVersion, int gridWidth, int gridHeight, int cellSizePx,
                              ResizeMode resizeMode, List<TokenResolution> tokenResolutions) {
        this(expectedVersion, gridWidth, gridHeight, cellSizePx, resizeMode,
                tokenResolutions, List.of(), null, null, null, null);
    }

    public MapSettingsCommand(long expectedVersion, int gridWidth, int gridHeight, int cellSizePx,
                              ResizeMode resizeMode, List<TokenResolution> tokenResolutions,
                              MapDocumentDto document) {
        this(expectedVersion, gridWidth, gridHeight, cellSizePx, resizeMode,
                tokenResolutions, List.of(), null, null, null, document);
    }

    public enum ResizeMode { PRESERVE, CROP }

    public record TokenResolution(
            @JsonProperty(required = true) UUID tokenId,
            @JsonProperty(required = true) TokenAction action,
            int positionX,
            int positionY
    ) {}

    public enum TokenAction { MOVE, REMOVE }

    public record ShapeRemoval(
            @JsonProperty(required = true) String layerId,
            @JsonProperty(required = true) int shapeIndex
    ) {}

    public record TokenSnapshot(
            @JsonProperty(required = true) UUID id,
            String name,
            String kind,
            int positionX,
            int positionY,
            int sizeCols,
            int sizeRows,
            String color,
            boolean hidden,
            UUID statBlockId,
            UUID partyMemberId,
            String notes,
            String icon
    ) {}
}
