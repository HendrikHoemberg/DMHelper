package dev.hendrikhoemberg.dmhelper.calendar.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/campaigns/{campaignId}/calendar")
public class CalendarController {

    private final CalendarService calendarService;
    private final CampaignRepository campaignRepository;

    public CalendarController(CalendarService calendarService, CampaignRepository campaignRepository) {
        this.calendarService = calendarService;
        this.campaignRepository = campaignRepository;
    }

    @ModelAttribute
    void addCampaign(@PathVariable UUID campaignId, Model model) {
        var campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        model.addAttribute("campaign", campaign);
        model.addAttribute("campaignId", campaignId);
    }

    @GetMapping
    String overview(@PathVariable UUID campaignId, Model model) {
        var date = calendarService.getCurrentDate(campaignId);
        var config = calendarService.getCalendarConfig(campaignId);
        model.addAttribute("currentDate", date);
        model.addAttribute("config", config);
        model.addAttribute("currentWeekday", calendarService.computeWeekdayName(date, config));
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/overview";
    }

    @PostMapping("/advance")
    String advance(@PathVariable UUID campaignId, @RequestParam(defaultValue = "1") int days, Model model) {
        var date = calendarService.advanceDays(campaignId, days);
        var config = calendarService.getCalendarConfig(campaignId);
        model.addAttribute("currentDate", date);
        model.addAttribute("config", config);
        model.addAttribute("currentWeekday", calendarService.computeWeekdayName(date, config));
        return "calendar/_current-date :: currentDateFragment";
    }

    @PostMapping("/set-date")
    String setDate(@PathVariable UUID campaignId,
                   @RequestParam int year, @RequestParam int month, @RequestParam int day,
                   Model model) {
        calendarService.setCurrentDate(campaignId, new CalendarService.InGameDate(year, month, day));
        var date = calendarService.getCurrentDate(campaignId);
        var config = calendarService.getCalendarConfig(campaignId);
        model.addAttribute("currentDate", date);
        model.addAttribute("config", config);
        model.addAttribute("currentWeekday", calendarService.computeWeekdayName(date, config));
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/overview";
    }

    @GetMapping("/config")
    String configForm(@PathVariable UUID campaignId, Model model) {
        model.addAttribute("config", calendarService.getCalendarConfig(campaignId));
        return "calendar/_config-form :: configForm";
    }

    @PostMapping("/config")
    String updateConfig(@PathVariable UUID campaignId,
                        @RequestParam String monthNames,
                        @RequestParam String monthLengths,
                        @RequestParam String weekdayNames,
                        Model model) {
        String[] mn = monthNames.split(",");
        String[] mlRaw = monthLengths.split(",");
        int[] ml = new int[mlRaw.length];
        for (int i = 0; i < mlRaw.length; i++) ml[i] = Integer.parseInt(mlRaw[i].trim());
        String[] wn = weekdayNames.split(",");
        calendarService.updateCalendarConfig(campaignId,
                new CalendarService.CalendarConfig(ml, mn, wn));
        return overview(campaignId, model);
    }

    @GetMapping("/events/new")
    String newEventForm(@PathVariable UUID campaignId, Model model) {
        return "calendar/_event-form :: eventForm";
    }

    @PostMapping("/events")
    String createEvent(@PathVariable UUID campaignId,
                       @RequestParam int year, @RequestParam int month, @RequestParam int day,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) UUID noteId, Model model) {
        calendarService.createEvent(campaignId,
                new CalendarService.InGameDate(year, month, day), title, body, noteId);
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/_timeline-list :: timelineList";
    }

    @GetMapping("/events/{eventId}/edit")
    String editEventForm(@PathVariable UUID campaignId, @PathVariable UUID eventId, Model model) {
        var events = calendarService.findByCampaignId(campaignId);
        var event = events.stream().filter(e -> e.id().equals(eventId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Event not found"));
        model.addAttribute("event", event);
        return "calendar/_event-form :: eventForm";
    }

    @PutMapping("/events/{eventId}")
    String updateEvent(@PathVariable UUID campaignId, @PathVariable UUID eventId,
                       @RequestParam int year, @RequestParam int month, @RequestParam int day,
                       @RequestParam String title, @RequestParam(required = false) String body,
                       @RequestParam(required = false) UUID noteId, Model model) {
        calendarService.updateEvent(eventId,
                new CalendarService.InGameDate(year, month, day), title, body, noteId);
        model.addAttribute("events", calendarService.findByCampaignId(campaignId));
        return "calendar/_timeline-list :: timelineList";
    }

    @DeleteMapping("/events/{eventId}")
    ResponseEntity<Void> deleteEvent(@PathVariable UUID campaignId, @PathVariable UUID eventId) {
        calendarService.deleteEvent(eventId);
        return ResponseEntity.ok().build();
    }
}
