package dev.hendrikhoemberg.dmhelper.campaign.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.LevelingMode;

public record CampaignSettings(
        LevelingMode levelingMode,
        @JsonProperty("calendarConfig") CalendarConfig calendar,
        @JsonProperty("currentInGameDate") InGameDate currentDate
) {
    public static CampaignSettings defaults() {
        return new CampaignSettings(LevelingMode.XP,
                new CalendarConfig(
                        new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
                        new String[]{"January", "February", "March", "April", "May", "June",
                                "July", "August", "September", "October", "November", "December"},
                        new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}
                ),
                new InGameDate(1492, 0, 1));
    }
}
