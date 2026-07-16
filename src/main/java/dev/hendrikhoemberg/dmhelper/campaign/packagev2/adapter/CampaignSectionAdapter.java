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

        Campaign campaign = resolveCampaign(context);
        campaign.setName(campaignDto.name());
        campaign.setDescription(campaignDto.description());
        campaign.setCreatedAt(campaignDto.createdAt());

        if (campaignDto.settings() != null) {
            CampaignSettings settings = fromSettingsDto(campaignDto.settings());
            codec.write(campaign, settings);
        }

        context.register(CampaignContentType.CAMPAIGN, campaignDto.key(), campaign, campaign.getId());

        if (campaignDto.currentSceneRef() != null) {
            Campaign captured = campaign;
            context.defer("currentSceneId", () -> {
                var scene = context.require(campaignDto.currentSceneRef(), CampaignContentType.SCENE, Scene.class);
                captured.setCurrentSceneId(scene.getId());
            });
        }
    }

    private static Campaign resolveCampaign(CampaignImportContext context) {
        Campaign campaign;
        try {
            campaign = context.campaign();
        } catch (IllegalStateException e) {
            campaign = new Campaign();
            context.setCampaign(campaign);
        }
        return campaign;
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
        CampaignSettings defaults = CampaignSettings.defaults();
        return new CampaignSettings(
                dto.levelingMode() != null ? dto.levelingMode() : defaults.levelingMode(),
                dto.calendar() != null
                        ? new CalendarConfig(
                                dto.calendar().monthLengths().stream().mapToInt(i -> i).toArray(),
                                dto.calendar().monthNames().toArray(String[]::new),
                                dto.calendar().weekdayNames().toArray(String[]::new))
                        : defaults.calendar(),
                dto.currentDate() != null
                        ? new InGameDate(dto.currentDate().year(), dto.currentDate().month(), dto.currentDate().day())
                        : defaults.currentDate()
        );
    }
}
