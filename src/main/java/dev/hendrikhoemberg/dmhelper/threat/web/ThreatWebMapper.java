package dev.hendrikhoemberg.dmhelper.threat.web;

import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheck;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatKind;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReference;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapDisarmMethod;

import java.util.List;
import java.util.UUID;

public final class ThreatWebMapper {

    private ThreatWebMapper() {}

    public static TrapResponse fromTrap(Trap trap) {
        UUID campaignId = trap.getCampaign() == null ? null : trap.getCampaign().getId();
        UUID statBlockId = trap.getStatBlock() == null ? null : trap.getStatBlock().getId();
        String statBlockLabel = trap.getStatBlock() == null ? null : trap.getStatBlock().getName();
        return new TrapResponse(
                ThreatKind.TRAP,
                trap.getId(),
                trap.getSourceKey(),
                trap.getSource(),
                campaignId,
                trapProvenance(trap.getProvenance()),
                trap.getName(),
                trap.getDescription(),
                trap.getSeverity(),
                trap.getMinLevel(),
                trap.getMaxLevel(),
                trap.getCreatedAt(),
                trap.getTriggerDescription(),
                trap.getTriggerAreaHint(),
                trap.getDetectionPassiveThreshold(),
                trapCheck(trap.getDetectionCheck()),
                trap.getDisarmMethods().stream().map(ThreatWebMapper::disarm).toList(),
                trap.getAttackBonus(),
                trapCheck(trap.getSave()),
                trap.getDamageExpression(),
                List.copyOf(trap.getDamageTypes()),
                trap.getAdditionalEffect(),
                trap.getResetMode(),
                trap.getResetTiming(),
                statBlockId,
                statBlockLabel,
                trap.getCountermeasureNotes(),
                trap.getReferences().stream().map(ThreatWebMapper::trapReference).toList());
    }

    public static HazardResponse fromHazard(Hazard hazard) {
        UUID campaignId = hazard.getCampaign() == null ? null : hazard.getCampaign().getId();
        return new HazardResponse(
                ThreatKind.HAZARD,
                hazard.getId(),
                hazard.getSourceKey(),
                hazard.getSource(),
                campaignId,
                hazardProvenance(hazard.getProvenance()),
                hazard.getName(),
                hazard.getDescription(),
                hazard.getSeverity(),
                hazard.getMinLevel(),
                hazard.getMaxLevel(),
                hazard.getCreatedAt(),
                hazard.getExposureMode(),
                hazard.getExposureText(),
                hazard.getAreaHint(),
                hazardCheck(hazard.getCheck()),
                hazard.getDamageExpression(),
                List.copyOf(hazard.getDamageTypes()),
                hazard.getEscalationText(),
                hazard.getEndingConditions(),
                hazard.getReferences().stream().map(ThreatWebMapper::hazardReference).toList());
    }

    public static ThreatCardView cardFromTrap(Trap trap, String descriptionHtml) {
        return new ThreatCardView(
                ThreatKind.TRAP,
                trap.getId(),
                trap.getName(),
                descriptionHtml,
                fromTrap(trap),
                null);
    }

    public static ThreatCardView cardFromHazard(Hazard hazard, String descriptionHtml) {
        return new ThreatCardView(
                ThreatKind.HAZARD,
                hazard.getId(),
                hazard.getName(),
                descriptionHtml,
                null,
                fromHazard(hazard));
    }

    private static TrapResponse.ProvenanceResponse trapProvenance(ContentProvenance provenance) {
        if (provenance == null) {
            return null;
        }
        return new TrapResponse.ProvenanceResponse(
                provenance.getSourceTitle(),
                provenance.getEditionVersion(),
                provenance.getSourceLocator(),
                provenance.getLicenseClassification(),
                provenance.getImportedAt(),
                provenance.getConverterId(),
                provenance.getConverterVersion(),
                provenance.getSourceHash(),
                provenance.getExtractionConfidence());
    }

    private static HazardResponse.ProvenanceResponse hazardProvenance(ContentProvenance provenance) {
        if (provenance == null) {
            return null;
        }
        return new HazardResponse.ProvenanceResponse(
                provenance.getSourceTitle(),
                provenance.getEditionVersion(),
                provenance.getSourceLocator(),
                provenance.getLicenseClassification(),
                provenance.getImportedAt(),
                provenance.getConverterId(),
                provenance.getConverterVersion(),
                provenance.getSourceHash(),
                provenance.getExtractionConfidence());
    }

    private static TrapResponse.CheckResponse trapCheck(ThreatCheck check) {
        if (check == null) {
            return null;
        }
        return new TrapResponse.CheckResponse(
                check.getMode(), check.getAbility(), check.getSkill(), check.getDc());
    }

    private static HazardResponse.CheckResponse hazardCheck(ThreatCheck check) {
        if (check == null) {
            return null;
        }
        return new HazardResponse.CheckResponse(
                check.getMode(), check.getAbility(), check.getSkill(), check.getDc());
    }

    private static TrapResponse.DisarmMethodResponse disarm(TrapDisarmMethod method) {
        return new TrapResponse.DisarmMethodResponse(
                method.getId(),
                method.getMethodKey(),
                method.getLabel(),
                method.getAbility(),
                method.getSkill(),
                method.getTool(),
                method.getDc(),
                method.getFailureConsequence(),
                method.getSortOrder());
    }

    private static TrapResponse.ReferenceResponse trapReference(ThreatReference reference) {
        return new TrapResponse.ReferenceResponse(
                reference.getId(),
                reference.getRole(),
                reference.getTargetType(),
                reference.getTargetId(),
                reference.getDisplayText(),
                reference.getSortOrder());
    }

    private static HazardResponse.ReferenceResponse hazardReference(ThreatReference reference) {
        return new HazardResponse.ReferenceResponse(
                reference.getId(),
                reference.getRole(),
                reference.getTargetType(),
                reference.getTargetId(),
                reference.getDisplayText(),
                reference.getSortOrder());
    }
}
