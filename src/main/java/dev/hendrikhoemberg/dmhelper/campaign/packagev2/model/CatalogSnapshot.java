package dev.hendrikhoemberg.dmhelper.campaign.packagev2.model;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;

import java.util.List;

public record CatalogSnapshot(
        String version,
        String sha256,
        List<Entry> entries
) {
    public record Entry(
            CampaignContentType type,
            String sourceKey,
            String name,
            String ruleset,
            String source,
            List<String> aliases
    ) {}
}
