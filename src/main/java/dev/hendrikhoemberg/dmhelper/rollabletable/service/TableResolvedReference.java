package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;

import java.util.UUID;

public record TableResolvedReference(
        CampaignContentType targetType,
        UUID targetId,
        String displayText) {
}
