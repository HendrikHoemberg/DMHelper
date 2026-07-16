package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LevelingMode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CampaignSettingsCodec {

    private final ObjectMapper objectMapper;

    public CampaignSettingsCodec(ObjectMapper objectMapper) {
        this.objectMapper = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
                .build();
    }

    public CampaignSettings read(Campaign campaign) {
        String json = campaign.getSettings();
        if (json == null || json.isBlank()) {
            return CampaignSettings.defaults();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            CampaignSettings settings = objectMapper.treeToValue(root, CampaignSettings.class);
            if (!root.has("levelingMode")) {
                LevelingMode mode = campaign.isMilestoneLeveling()
                        ? LevelingMode.MILESTONE
                        : LevelingMode.XP;
                settings = new CampaignSettings(mode, settings.calendar(), settings.currentDate());
            }
            return settings;
        } catch (Exception e) {
            throw new IllegalStateException("Campaign settings are malformed", e);
        }
    }

    public void write(Campaign campaign, CampaignSettings settings) {
        try {
            String json = objectMapper.writeValueAsString(settings);
            campaign.setSettings(json);
            campaign.setMilestoneLeveling(settings.levelingMode() == LevelingMode.MILESTONE);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write campaign settings", e);
        }
    }
}
