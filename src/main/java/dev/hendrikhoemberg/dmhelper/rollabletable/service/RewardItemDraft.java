package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;

import java.util.UUID;

public record RewardItemDraft(
        CampaignContentType type,
        UUID targetId,
        String displayName,
        int quantity) {
}
