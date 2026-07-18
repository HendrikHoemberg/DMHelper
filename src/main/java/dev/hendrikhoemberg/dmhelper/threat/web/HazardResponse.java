package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import dev.hendrikhoemberg.dmhelper.threat.data.DamageType;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stable JSON representation of a hazard. Persistence relationships are
 * deliberately mapped rather than serialized so bidirectional JPA back-links
 * can never leak into the API response graph.
 */
public record HazardResponse(
        ThreatKind kind,
        UUID id,
        String sourceKey,
        ContentSource source,
        UUID campaignId,
        ProvenanceResponse provenance,
        String name,
        String description,
        ThreatSeverity severity,
        Integer minLevel,
        Integer maxLevel,
        Instant createdAt,
        HazardExposureMode exposureMode,
        String exposureText,
        String areaHint,
        CheckResponse check,
        String damageExpression,
        List<DamageType> damageTypes,
        String escalationText,
        String endingConditions,
        List<ReferenceResponse> references
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

    public record CheckResponse(
            ThreatCheckMode mode,
            String ability,
            String skill,
            Integer dc
    ) {}

    public record ReferenceResponse(
            UUID id,
            ThreatReferenceRole role,
            CampaignContentType targetType,
            UUID targetId,
            String displayText,
            int sortOrder
    ) {}
}
