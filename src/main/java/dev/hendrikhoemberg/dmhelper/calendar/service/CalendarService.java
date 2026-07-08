package dev.hendrikhoemberg.dmhelper.calendar.service;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class CalendarService {

    public static final CalendarConfig DEFAULT_CALENDAR = new CalendarConfig(
        new int[]{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31},
        new String[]{"January", "February", "March", "April", "May", "June",
                     "July", "August", "September", "October", "November", "December"},
        new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"}
    );

    private final CampaignRepository campaignRepository;
    private final TimelineEventRepository timelineEventRepository;
    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;

    public CalendarService(CampaignRepository campaignRepository,
                           TimelineEventRepository timelineEventRepository,
                           NoteRepository noteRepository,
                           ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.noteRepository = noteRepository;
        this.objectMapper = objectMapper;
    }

    public record CalendarConfig(int[] monthLengths, String[] monthNames, String[] weekdayNames) {
        @Override public boolean equals(Object o) {
            if (!(o instanceof CalendarConfig other)) return false;
            return Arrays.equals(monthLengths, other.monthLengths)
                && Arrays.equals(monthNames, other.monthNames)
                && Arrays.equals(weekdayNames, other.weekdayNames);
        }
        @Override public int hashCode() { return Objects.hash(Arrays.hashCode(monthLengths), Arrays.hashCode(monthNames), Arrays.hashCode(weekdayNames)); }
    }

    public record InGameDate(int year, int month, int day) {}

    public record TimelineEventDto(
        UUID id, UUID campaignId, int inGameYear, int inGameMonth, int inGameDay,
        String title, String body, UUID noteId, String relativeLabel
    ) {
        public static TimelineEventDto from(TimelineEvent te, String relativeLabel) {
            return new TimelineEventDto(
                te.getId(), te.getCampaign().getId(),
                te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(),
                te.getTitle(), te.getBody(),
                te.getNoteRef() != null ? te.getNoteRef().getId() : null,
                relativeLabel
            );
        }
    }

    // --- Calendar Config ---

    public CalendarConfig getCalendarConfig(UUID campaignId) {
        Map<String, Object> settings = readSettings(campaignId);
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) settings.get("calendarConfig");
        if (cfg == null) return DEFAULT_CALENDAR;

        @SuppressWarnings("unchecked")
        List<Integer> mlRaw = (List<Integer>) cfg.get("monthLengths");
        @SuppressWarnings("unchecked")
        List<String> mnRaw = (List<String>) cfg.get("monthNames");
        @SuppressWarnings("unchecked")
        List<String> wnRaw = (List<String>) cfg.get("weekdayNames");

        int[] monthLengths = mlRaw != null ? mlRaw.stream().mapToInt(i -> i).toArray() : DEFAULT_CALENDAR.monthLengths();
        String[] monthNames = mnRaw != null ? mnRaw.toArray(String[]::new) : DEFAULT_CALENDAR.monthNames();
        String[] weekdayNames = wnRaw != null ? wnRaw.toArray(String[]::new) : DEFAULT_CALENDAR.weekdayNames();
        return new CalendarConfig(monthLengths, monthNames, weekdayNames);
    }

    public void updateCalendarConfig(UUID campaignId, CalendarConfig config) {
        Map<String, Object> settings = readSettings(campaignId);
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("monthLengths", Arrays.asList(Arrays.stream(config.monthLengths()).boxed().toArray(Integer[]::new)));
        cfg.put("monthNames", List.of(config.monthNames()));
        cfg.put("weekdayNames", List.of(config.weekdayNames()));
        settings.put("calendarConfig", cfg);
        writeSettings(campaignId, settings);
    }

    // --- Current Date ---

    public InGameDate getCurrentDate(UUID campaignId) {
        Map<String, Object> settings = readSettings(campaignId);
        @SuppressWarnings("unchecked")
        Map<String, Object> date = (Map<String, Object>) settings.get("currentInGameDate");
        if (date == null) return new InGameDate(1492, 0, 1);
        return new InGameDate(
            ((Number) date.getOrDefault("year", 1492)).intValue(),
            ((Number) date.getOrDefault("month", 0)).intValue(),
            ((Number) date.getOrDefault("day", 1)).intValue()
        );
    }

    public void setCurrentDate(UUID campaignId, InGameDate date) {
        Map<String, Object> settings = readSettings(campaignId);
        Map<String, Object> dateObj = new LinkedHashMap<>();
        dateObj.put("year", date.year());
        dateObj.put("month", date.month());
        dateObj.put("day", date.day());
        settings.put("currentInGameDate", dateObj);
        writeSettings(campaignId, settings);
    }

    public InGameDate advanceDays(UUID campaignId, int days) {
        CalendarConfig cfg = getCalendarConfig(campaignId);
        InGameDate current = getCurrentDate(campaignId);
        int year = current.year();
        int month = current.month();
        int day = current.day();

        for (int i = 0; i < days; i++) {
            day++;
            if (day > cfg.monthLengths()[month]) {
                day = 1;
                month++;
                if (month >= cfg.monthLengths().length) {
                    month = 0;
                    year++;
                }
            }
        }
        InGameDate newDate = new InGameDate(year, month, day);
        setCurrentDate(campaignId, newDate);
        return newDate;
    }

    // --- Timeline Events ---

    public TimelineEventDto createEvent(UUID campaignId, InGameDate date, String title, String body, UUID noteId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        TimelineEvent te = new TimelineEvent();
        te.setCampaign(campaign);
        te.setInGameYear(date.year());
        te.setInGameMonth(date.month());
        te.setInGameDay(date.day());
        te.setTitle(title);
        te.setBody(body);
        if (noteId != null) {
            te.setNoteRef(noteRepository.findById(noteId)
                    .orElseThrow(() -> new NotFoundException("Note not found: " + noteId)));
        }
        te = timelineEventRepository.save(te);
        String label = computeRelativeLabel(campaignId, te);
        return TimelineEventDto.from(te, label);
    }

    public TimelineEventDto updateEvent(UUID id, InGameDate date, String title, String body, UUID noteId) {
        TimelineEvent te = timelineEventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Timeline event not found: " + id));
        te.setInGameYear(date.year());
        te.setInGameMonth(date.month());
        te.setInGameDay(date.day());
        te.setTitle(title);
        te.setBody(body);
        if (noteId != null) {
            te.setNoteRef(noteRepository.findById(noteId).orElse(null));
        } else {
            te.setNoteRef(null);
        }
        te = timelineEventRepository.save(te);
        String label = computeRelativeLabel(te.getCampaign().getId(), te);
        return TimelineEventDto.from(te, label);
    }

    public void deleteEvent(UUID id) {
        if (!timelineEventRepository.existsById(id)) {
            throw new NotFoundException("Timeline event not found: " + id);
        }
        timelineEventRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<TimelineEventDto> findByCampaignId(UUID campaignId) {
        List<TimelineEvent> events = timelineEventRepository
                .findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(campaignId);
        InGameDate current = getCurrentDate(campaignId);
        CalendarConfig cfg = getCalendarConfig(campaignId);
        return events.stream()
                .map(e -> TimelineEventDto.from(e, computeRelativeLabel(current, e, cfg)))
                .toList();
    }

    // --- Helpers ---

    private String computeRelativeLabel(UUID campaignId, TimelineEvent te) {
        return computeRelativeLabel(getCurrentDate(campaignId), te, getCalendarConfig(campaignId));
    }

    private String computeRelativeLabel(InGameDate current, TimelineEvent te, CalendarConfig cfg) {
        int currentDays = dateToEpochDays(current, cfg);
        int eventDays = dateToEpochDays(te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(), cfg);
        int diff = eventDays - currentDays;
        if (diff < 0) return Math.abs(diff) + (Math.abs(diff) == 1 ? " day ago" : " days ago");
        if (diff == 0) return "today";
        if (diff == 1) return "tomorrow";
        return "in " + diff + " days";
    }

    private int dateToEpochDays(InGameDate date, CalendarConfig cfg) {
        return dateToEpochDays(date.year(), date.month(), date.day(), cfg);
    }

    private int dateToEpochDays(int year, int month, int day, CalendarConfig cfg) {
        int days = 0;
        for (int y = 0; y < year; y++) days += yearDays(cfg);
        for (int m = 0; m < month; m++) days += cfg.monthLengths()[m];
        return days + day;
    }

    private int yearDays(CalendarConfig cfg) {
        int total = 0;
        for (int len : cfg.monthLengths()) total += len;
        return total;
    }

    // --- Settings JSON helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSettings(UUID campaignId) {
        Campaign c = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        if (c.getSettings() == null || c.getSettings().isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(c.getSettings(), Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void writeSettings(UUID campaignId, Map<String, Object> settings) {
        Campaign c = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        try {
            c.setSettings(objectMapper.writeValueAsString(settings));
            campaignRepository.save(c);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save settings", e);
        }
    }
}
