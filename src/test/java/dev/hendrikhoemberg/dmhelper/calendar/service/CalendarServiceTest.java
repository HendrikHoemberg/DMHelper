package dev.hendrikhoemberg.dmhelper.calendar.service;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.CalendarConfig;
import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService.InGameDate;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import({CalendarService.class, CampaignSettingsCodec.class})
class CalendarServiceTest {

    @Autowired private CalendarService service;
    @Autowired private EntityManager em;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test");
        campaign.setSettings("""
                {"calendarConfig":{"monthLengths":[31,28,31,30,31,30,31,31,30,31,30,31],
                "monthNames":["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"],
                "weekdayNames":["Mo","Tu","We","Th","Fr","Sa","Su"]},
                "currentInGameDate":{"year":1492,"month":2,"day":15}}""");
        em.persist(campaign);
        em.flush();
    }

    @Test
    void shouldReadCalendarConfig() {
        CalendarConfig cfg = service.getCalendarConfig(campaign.getId());
        assertThat(cfg.monthNames()).containsExactly("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec");
        assertThat(cfg.monthLengths()[1]).isEqualTo(28);
    }

    @Test
    void shouldReturnDefaultsWhenNoConfig() {
        Campaign empty = new Campaign();
        empty.setName("Empty");
        em.persist(empty);
        em.flush();

        CalendarConfig cfg = service.getCalendarConfig(empty.getId());
        assertThat(cfg.monthNames()).contains("January");
    }

    @Test
    void shouldReadCurrentDate() {
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.year()).isEqualTo(1492);
        assertThat(date.month()).isEqualTo(2);
        assertThat(date.day()).isEqualTo(15);
    }

    @Test
    void shouldSetCurrentDate() {
        service.setCurrentDate(campaign.getId(), new InGameDate(1500, 5, 10));
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.year()).isEqualTo(1500);
        assertThat(date.month()).isEqualTo(5);
        assertThat(date.day()).isEqualTo(10);
    }

    @Test
    void shouldAdvanceDaysWithinMonth() {
        service.advanceDays(campaign.getId(), 1);
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.day()).isEqualTo(16);
        assertThat(date.month()).isEqualTo(2);
    }

    @Test
    void shouldAdvanceDaysAcrossMonth() {
        service.advanceDays(campaign.getId(), 20);
        InGameDate date = service.getCurrentDate(campaign.getId());
        assertThat(date.month()).isEqualTo(3);
        assertThat(date.day()).isEqualTo(4);
    }

    @Test
    void shouldCreateTimelineEvent() {
        var event = service.createEvent(campaign.getId(),
                new InGameDate(1492, 3, 30), "Solar Eclipse", "The sky darkens", null);
        assertThat(event.title()).isEqualTo("Solar Eclipse");
        assertThat(event.relativeLabel()).isNotNull();
    }

    @Test
    void shouldReturnTimelineSortedByDate() {
        service.createEvent(campaign.getId(), new InGameDate(1493, 0, 1), "Later", null, null);
        service.createEvent(campaign.getId(), new InGameDate(1492, 0, 1), "Earlier", null, null);

        var events = service.findByCampaignId(campaign.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).title()).isEqualTo("Earlier");
        assertThat(events.get(1).title()).isEqualTo("Later");
    }

    @Test
    void shouldComputeRelativeLabels() {
        service.createEvent(campaign.getId(),
                new InGameDate(1492, 2, 15), "Today Event", null, null);
        service.createEvent(campaign.getId(),
                new InGameDate(1492, 2, 16), "Tomorrow Event", null, null);
        service.createEvent(campaign.getId(),
                new InGameDate(1492, 2, 10), "Past Event", null, null);

        var events = service.findByCampaignId(campaign.getId());
        assertThat(events.get(0).relativeLabel()).isEqualTo("5 days ago");
        assertThat(events.get(1).relativeLabel()).isEqualTo("today");
        assertThat(events.get(2).relativeLabel()).isEqualTo("tomorrow");
    }

    @Test
    void shouldDeleteTimelineEvent() {
        var event = service.createEvent(campaign.getId(),
                new InGameDate(1492, 3, 1), "Delete Me", null, null);
        service.deleteEvent(event.id());
        var events = service.findByCampaignId(campaign.getId());
        assertThat(events).isEmpty();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
