package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import tools.jackson.databind.JsonNode;

import java.util.Comparator;

public record CampaignSemanticSnapshot(CampaignManifestV2 manifest, JsonNode persistenceProjection) {

    public static CampaignSemanticSnapshot from(CampaignManifestV2 source) {
        return new CampaignSemanticSnapshot(source, null);
    }

    static CampaignManifestV2.Metadata withoutCreatedAt(CampaignManifestV2.Metadata md) {
        return new CampaignManifestV2.Metadata(
                md.packageKey(), null, md.generator(),
                md.catalogVersion(), md.catalogSha256(), md.exclusions());
    }

    static <T> java.util.List<T> sortByKey(java.util.List<T> list, java.util.function.Function<T, String> keyFn) {
        if (list == null || list.isEmpty()) return list == null ? java.util.List.of() : list;
        return list.stream()
                .sorted(Comparator.comparing(keyFn, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
