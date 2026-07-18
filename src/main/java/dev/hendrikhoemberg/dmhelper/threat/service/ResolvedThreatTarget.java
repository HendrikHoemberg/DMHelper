package dev.hendrikhoemberg.dmhelper.threat.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;

import java.util.UUID;

public record ResolvedThreatTarget(
        CampaignContentType type, UUID id, String displayName,
        ContentSource source, UUID campaignId) {
}
