package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatReferenceRole;

import java.util.UUID;

public record ThreatReferenceWrite(
        ThreatReferenceRole role, CampaignContentType targetType,
        UUID targetId, String displayText) {
}
