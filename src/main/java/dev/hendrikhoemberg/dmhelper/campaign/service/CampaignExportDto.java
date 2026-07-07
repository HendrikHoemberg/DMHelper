package dev.hendrikhoemberg.dmhelper.campaign.service;

import tools.jackson.annotation.JsonInclude;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;

import java.util.List;

public record CampaignExportDto(
        int formatVersion,
        CampaignDto campaign,
        List<Object> party,
        List<Object> statBlocks,
        List<Object> handouts,
        List<Object> maps,
        List<Object> encounters,
        List<Object> notes
) {
    private static final int CURRENT_FORMAT_VERSION = 1;

    public static CampaignExportDto from(Campaign campaign) {
        return new CampaignExportDto(
                CURRENT_FORMAT_VERSION,
                new CampaignDto(campaign.getName(), campaign.getDescription()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public record CampaignDto(
            String name,
            @JsonInclude(JsonInclude.Include.NON_DEFAULT) String description
    ) {
        public CampaignDto {
            if (description != null && description.isBlank()) {
                description = null;
            }
        }
    }
}
