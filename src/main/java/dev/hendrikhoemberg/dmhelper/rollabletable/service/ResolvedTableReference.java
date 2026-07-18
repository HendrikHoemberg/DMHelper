package dev.hendrikhoemberg.dmhelper.rollabletable.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableReferenceScope;
import java.util.UUID;

public record ResolvedTableReference(
        TableReferenceScope scope, CampaignContentType targetType, UUID targetId,
        String catalogRuleset, String catalogSourceKey, String displayText) {
}
