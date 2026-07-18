package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stable JSON representation of a rollable table. Persistence relationships are
 * deliberately mapped rather than serialized so bidirectional JPA back-links
 * can never leak into the API response graph.
 */
public record RollableTableResponse(
        UUID id,
        String sourceKey,
        ContentSource source,
        UUID campaignId,
        ProvenanceResponse provenance,
        String name,
        String description,
        TableAddressMode addressMode,
        String rollExpression,
        TableCategory category,
        String tags,
        Instant createdAt,
        List<EntryResponse> entries
) {

    public record ProvenanceResponse(
            String sourceTitle,
            String editionVersion,
            String sourceLocator,
            LicenseClassification licenseClassification,
            Instant importedAt,
            String converterId,
            String converterVersion,
            String sourceHash,
            SourceAnnotationConfidence extractionConfidence
    ) {}

    public record EntryResponse(
            UUID id,
            String entryKey,
            Integer rangeStart,
            Integer rangeEnd,
            Integer weight,
            String resultText,
            String quantityExpression,
            int sortOrder,
            List<ReferenceResponse> references
    ) {}

    public record ReferenceResponse(
            UUID id,
            TableReferenceScope targetScope,
            String targetType,
            UUID targetId,
            String catalogRuleset,
            String catalogSourceKey,
            String displayText,
            int sortOrder
    ) {}
}
