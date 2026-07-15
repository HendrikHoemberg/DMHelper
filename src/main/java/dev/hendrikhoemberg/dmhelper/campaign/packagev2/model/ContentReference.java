package dev.hendrikhoemberg.dmhelper.campaign.packagev2.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentReference(
        Scope scope,
        CampaignContentType type,
        String key,
        String ruleset,
        String sourceKey
) {
    public enum Scope { PACKAGE, CATALOG }

    public static ContentReference packageRef(CampaignContentType type, String key) {
        return new ContentReference(Scope.PACKAGE, type, key, null, null);
    }

    public static ContentReference catalogRef(CampaignContentType type, String ruleset, String sourceKey) {
        return new ContentReference(Scope.CATALOG, type, null, ruleset, sourceKey);
    }
}
