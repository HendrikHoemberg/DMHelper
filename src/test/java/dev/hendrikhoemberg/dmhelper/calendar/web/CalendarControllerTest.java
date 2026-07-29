package dev.hendrikhoemberg.dmhelper.calendar.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CalendarController.class)
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarService calendarService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void shouldShowOverview() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(new Campaign()));
        when(calendarService.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 0, 1));
        when(calendarService.getCalendarConfig(campaignId)).thenReturn(CalendarService.DEFAULT_CALENDAR);
        when(calendarService.findByCampaignId(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{campaignId}/calendar", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/overview"))
                .andExpect(model().attributeExists("currentDate"))
                .andExpect(model().attributeExists("events"));
    }

    @Test
    void emptyTimelineCtaOpensTheEventFormInPlace() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(new Campaign()));
        when(calendarService.getCurrentDate(campaignId)).thenReturn(new CalendarService.InGameDate(1492, 0, 1));
        when(calendarService.getCalendarConfig(campaignId)).thenReturn(CalendarService.DEFAULT_CALENDAR);
        when(calendarService.findByCampaignId(campaignId)).thenReturn(List.of());

        String body = mockMvc.perform(get("/campaigns/{campaignId}/calendar", campaignId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int ctaAt = body.indexOf("empty-state__cta");
        assertThat(ctaAt).as("an empty timeline offers a way to add the first event").isGreaterThan(-1);
        String cta = body.substring(body.lastIndexOf("<", ctaAt), body.indexOf(">", ctaAt) + 1);

        assertThat(cta)
                .as("/calendar/events/new serves only the form fragment, so navigating to it renders an unstyled page")
                .doesNotContain("href=")
                .contains("hx-get=\"/campaigns/" + campaignId + "/calendar/events/new\"")
                .contains("hx-target=\"#event-form-area\"");
    }

    @Test
    void shouldAdvanceDays() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(new Campaign()));
        when(calendarService.advanceDays(eq(campaignId), anyInt()))
                .thenReturn(new CalendarService.InGameDate(1492, 0, 2));
        when(calendarService.getCalendarConfig(campaignId)).thenReturn(CalendarService.DEFAULT_CALENDAR);

        mockMvc.perform(post("/campaigns/{campaignId}/calendar/advance", campaignId)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("calendar/_current-date :: currentDateFragment"));
    }
}
