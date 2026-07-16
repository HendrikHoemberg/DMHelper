package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LevelingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

class CampaignSettingsCodecTest {

    private CampaignSettingsCodec codec;

    @BeforeEach
    void setUp() {
        codec = new CampaignSettingsCodec(new ObjectMapper());
    }

    @Test
    void shouldReturnDefaultsForBlankSettings() {
        Campaign campaign = new Campaign();
        campaign.setName("test");
        campaign.setSettings(null);
        assertThat(codec.read(campaign)).isEqualTo(CampaignSettings.defaults());

        campaign.setSettings("");
        assertThat(codec.read(campaign)).isEqualTo(CampaignSettings.defaults());

        campaign.setSettings("   ");
        assertThat(codec.read(campaign)).isEqualTo(CampaignSettings.defaults());
    }

    @Test
    void shouldDecodeExactExistingJson() {
        Campaign campaign = createCampaignWithSettings("""
                {"calendarConfig":{"monthLengths":[31,28,31,30,31,30,31,31,30,31,30,31],
                "monthNames":["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
                "weekdayNames":["Mo","Tu","We","Th","Fr","Sa","Su"]},
                "currentInGameDate":{"year":1492,"month":2,"day":15}}""");

        CampaignSettings settings = codec.read(campaign);
        assertThat(settings.levelingMode()).isEqualTo(LevelingMode.XP);
        assertThat(settings.calendar().monthNames()).containsExactly(
                "Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
        assertThat(settings.currentDate()).isEqualTo(new InGameDate(1492, 2, 15));
    }

    @Test
    void shouldFailForMalformedNonblankJson() {
        Campaign campaign = createCampaignWithSettings("{invalid json}");
        assertThatThrownBy(() -> codec.read(campaign))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Campaign settings are malformed");
    }

    @Test
    void shouldReconcileMilestoneMode() {
        Campaign campaign = createCampaignWithSettings("""
                {"calendarConfig":{"monthLengths":[31,28,31,30,31,30,31,31,30,31,30,31],
                "monthNames":["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
                "weekdayNames":["Mo","Tu","We","Th","Fr","Sa","Su"]},
                "currentInGameDate":{"year":1492,"month":0,"day":1}}""");
        campaign.setMilestoneLeveling(true);

        CampaignSettings settings = codec.read(campaign);
        assertThat(settings.levelingMode()).isEqualTo(LevelingMode.MILESTONE);
    }

    @Test
    void shouldRejectUnknownSettings() {
        Campaign campaign = createCampaignWithSettings("""
                {"levelingMode":"XP","unknownKey":"shouldFail",
                "calendarConfig":{"monthLengths":[31,28,31,30,31,30,31,31,30,31,30,31],
                "monthNames":["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
                "weekdayNames":["Mo","Tu","We","Th","Fr","Sa","Su"]},
                "currentInGameDate":{"year":1492,"month":0,"day":1}}""");

        assertThatThrownBy(() -> codec.read(campaign))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Campaign settings are malformed");
    }

    @Test
    void shouldWriteAndReadBack() {
        Campaign campaign = new Campaign();
        campaign.setName("test");
        campaign.setSettings("");

        CampaignSettings original = new CampaignSettings(
                LevelingMode.MILESTONE,
                new CalendarConfig(new int[]{30, 30}, new String[]{"A", "B"}, new String[]{"X", "Y"}),
                new InGameDate(1500, 1, 15)
        );
        codec.write(campaign, original);

        assertThat(campaign.getSettings()).isNotNull();
        assertThat(campaign.isMilestoneLeveling()).isTrue();

        CampaignSettings readBack = codec.read(campaign);
        assertThat(readBack).isEqualTo(original);
    }

    @Test
    void shouldPreserveCreatedAt() {
        Campaign campaign = new Campaign();
        campaign.setName("test");
        campaign.setCreatedAt(java.time.Instant.now());

        var createdAt = campaign.getCreatedAt();
        CampaignSettings settings = CampaignSettings.defaults();
        codec.write(campaign, settings);

        assertThat(campaign.getCreatedAt()).isEqualTo(createdAt);
    }

    private static Campaign createCampaignWithSettings(String json) {
        Campaign campaign = new Campaign();
        campaign.setName("test");
        campaign.setSettings(json);
        return campaign;
    }
}
