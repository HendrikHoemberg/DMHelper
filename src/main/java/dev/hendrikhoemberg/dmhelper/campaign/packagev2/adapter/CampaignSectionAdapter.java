package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CalendarConfigDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CampaignDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.CampaignSettingsDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.InGameDateDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LevelingMode;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class CampaignSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final CampaignSettingsCodec codec;

    public CampaignSectionAdapter(CampaignSettingsCodec codec) {
        this.codec = codec;
    }

    @Override
    public String sectionName() {
        return "Campaign";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        Campaign campaign = context.campaign();
        CampaignSettings settings = codec.read(campaign);

        String key = context.key(CampaignContentType.CAMPAIGN, campaign.getId(), campaign.getName());

        CampaignSettingsDto settingsDto = toSettingsDto(settings);

        ContentReference currentSceneRef = null;
        if (campaign.getCurrentSceneId() != null) {
            currentSceneRef = context.packageRef(CampaignContentType.SCENE,
                    campaign.getCurrentSceneId(), "current-scene");
        }

        CampaignDto campaignDto = new CampaignDto(
                key, campaign.getName(), campaign.getDescription(),
                campaign.getCreatedAt(), settingsDto, currentSceneRef
        );
        target.campaign(campaignDto);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        CampaignDto campaignDto = source.campaign();

        Campaign campaign = new Campaign();
        campaign.setName(campaignDto.name());
        campaign.setDescription(campaignDto.description());

        if (campaignDto.settings() != null) {
            CampaignSettings settings = fromSettingsDto(campaignDto.settings());
            codec.write(campaign, settings);
        }

        context.setCampaign(campaign);

        context.register(CampaignContentType.CAMPAIGN, campaignDto.key(), campaign, campaign.getId());

        if (campaignDto.currentSceneRef() != null) {
            context.defer("currentSceneId", () -> {
                var scene = context.require(campaignDto.currentSceneRef(), CampaignContentType.SCENE, Scene.class);
                campaign.setCurrentSceneId(scene.getId());
            });
        }
    }

    static CampaignSettingsDto toSettingsDto(CampaignSettings settings) {
        return new CampaignSettingsDto(
                settings.levelingMode(),
                new CalendarConfigDto(
                        Arrays.stream(settings.calendar().monthLengths()).boxed().toList(),
                        List.of(settings.calendar().monthNames()),
                        List.of(settings.calendar().weekdayNames())
                ),
                new InGameDateDto(
                        settings.currentDate().year(),
                        settings.currentDate().month(),
                        settings.currentDate().day()
                )
        );
    }

    static CampaignSettings fromSettingsDto(CampaignSettingsDto dto) {
        return new CampaignSettings(
                dto.levelingMode() != null ? dto.levelingMode() : LevelingMode.XP,
                new CalendarConfig(
                        dto.calendar() != null
                                ? dto.calendar().monthLengths().stream().mapToInt(i -> i).toArray()
                                : new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
                        dto.calendar() != null
                                ? dto.calendar().monthNames().toArray(String[]::new)
                                : new String[]{"January", "February", "March", "April", "May", "June",
                                "July", "August", "September", "October", "November", "December"},
                        dto.calendar() != null
                                ? dto.calendar().weekdayNames().toArray(String[]::new)
                                : new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}
                ),
                dto.currentDate() != null
                        ? new InGameDate(dto.currentDate().year(), dto.currentDate().month(), dto.currentDate().day())
                        : new InGameDate(1492, 0, 1)
        );
    }
}
