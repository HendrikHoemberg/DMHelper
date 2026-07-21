package dev.hendrikhoemberg.dmhelper.calendar;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CalendarFormatDateTest {

    @Autowired private CalendarService calendarService;
    @Autowired private CampaignRepository campaignRepository;

    @Test
    void formatsDayMonthNameYearUsingCampaignCalendar() {
        Campaign c = new Campaign();
        c.setName("Cal");
        UUID id = campaignRepository.save(c).getId();

        String formatted = calendarService.formatDate(id,
                new CalendarService.InGameDate(1492, 3, 15));

        assertThat(formatted).isEqualTo("15 April 1492");
    }
}
