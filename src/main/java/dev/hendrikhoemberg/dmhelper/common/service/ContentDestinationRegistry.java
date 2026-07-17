package dev.hendrikhoemberg.dmhelper.common.service;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class ContentDestinationRegistry {
    public enum CampaignType {
        NOTE, QUICK_NOTE, MAP, ENCOUNTER, HANDOUT, PARTY_MEMBER, PARTY_MEMBER_SHEET, SCENE
    }

    public enum LibraryType {
        STATBLOCK(null), SPELL("spells"), CONDITION("conditions"), RULE("rules"),
        EQUIPMENT("equipment"), MAGIC_ITEM("magic-items"), CLASS(null),
        SPECIES("species"), BACKGROUND("backgrounds"), FEAT("feats");

        private final String tab;

        LibraryType(String tab) {
            this.tab = tab;
        }
    }

    public String campaign(CampaignType type, UUID campaignId, UUID entityId, UUID parentId) {
        String root = "/campaigns/" + campaignId;
        return switch (type) {
            case NOTE -> root + "/notes/" + entityId;
            case QUICK_NOTE -> root + "/notes";
            case MAP -> root + "/maps/" + entityId + "/play";
            case ENCOUNTER -> root + "/encounters/" + entityId;
            case HANDOUT -> root + "/handouts#handout-" + entityId;
            case PARTY_MEMBER -> root + "/party#pm-card-" + entityId;
            case PARTY_MEMBER_SHEET -> root + "/party/" + entityId + "/sheet";
            case SCENE -> {
                if (parentId == null) throw new IllegalArgumentException("Scene destination requires adventure ID");
                yield root + "/adventures/" + parentId + "/scenes/" + entityId;
            }
        };
    }

    public String library(LibraryType type, UUID entityId, String sourceKey, String displayName) {
        return switch (type) {
            case STATBLOCK -> "/library/statblocks/" + entityId;
            case CLASS -> {
                if (entityId != null) {
                    yield "/library/classes/id/" + entityId;
                }
                if (sourceKey == null || sourceKey.isBlank()) {
                    yield filtered("classes", displayName);
                }
                yield "/library/classes/" + encodePathSegment(sourceKey);
            }
            case SPELL -> entityId != null
                    ? "/library/spells/" + entityId
                    : filtered("spells", displayName);
            case CONDITION -> entityId != null
                    ? "/library/conditions/" + entityId
                    : filtered("conditions", displayName);
            case RULE -> entityId != null
                    ? "/library/rules/" + entityId
                    : filtered("rules", displayName);
            case EQUIPMENT -> entityId != null
                    ? "/library/equipment/" + entityId
                    : filtered("equipment", displayName);
            case MAGIC_ITEM -> entityId != null
                    ? "/library/magic-items/" + entityId
                    : filtered("magic-items", displayName);
            case SPECIES -> entityId != null
                    ? "/library/species/" + entityId
                    : filtered("species", displayName);
            case BACKGROUND -> entityId != null
                    ? "/library/backgrounds/" + entityId
                    : filtered("backgrounds", displayName);
            case FEAT -> entityId != null
                    ? "/library/feats/" + entityId
                    : filtered("feats", displayName);
        };
    }

    private String filtered(String tab, String displayName) {
        return "/library?tab=" + encodeQuery(tab) + "&search=" + encodeQuery(displayName);
    }

    private String encodeQuery(String value) {
        return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
