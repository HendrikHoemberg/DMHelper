package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioSwitchMode;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LevelingMode;

public record CampaignSettings(
        LevelingMode levelingMode,
        @JsonProperty("calendarConfig") CalendarConfig calendar,
        @JsonProperty("currentInGameDate") InGameDate currentDate,
        @JsonProperty("audioSwitchMode") AudioSwitchMode audioSwitchMode
) {
    public static CampaignSettings defaults() {
        return new CampaignSettings(LevelingMode.XP,
                CalendarService.DEFAULT_CALENDAR,
                new InGameDate(1492, 0, 1),
                AudioSwitchMode.AUTOMATIC);
    }
}
