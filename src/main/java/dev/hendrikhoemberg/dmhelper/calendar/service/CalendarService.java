package dev.hendrikhoemberg.dmhelper.calendar.service;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettings;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignSettingsCodec;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
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
    private final CampaignSettingsCodec settingsCodec;

    public CalendarService(CampaignRepository campaignRepository,
                           TimelineEventRepository timelineEventRepository,
                           NoteRepository noteRepository,
                           CampaignSettingsCodec settingsCodec) {
        this.campaignRepository = campaignRepository;
        this.timelineEventRepository = timelineEventRepository;
        this.noteRepository = noteRepository;
        this.settingsCodec = settingsCodec;
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
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        return settingsCodec.read(campaign).calendar();
    }

    public void updateCalendarConfig(UUID campaignId, CalendarConfig config) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        CampaignSettings settings = settingsCodec.read(campaign);
        CampaignSettings updated = new CampaignSettings(settings.levelingMode(), config, settings.currentDate(), settings.audioSwitchMode());
        settingsCodec.write(campaign, updated);
        campaignRepository.save(campaign);
    }

    // --- Current Date ---

    public InGameDate getCurrentDate(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        return settingsCodec.read(campaign).currentDate();
    }

    public void setCurrentDate(UUID campaignId, InGameDate date) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found"));
        CampaignSettings settings = settingsCodec.read(campaign);
        CampaignSettings updated = new CampaignSettings(settings.levelingMode(), settings.calendar(), date, settings.audioSwitchMode());
        settingsCodec.write(campaign, updated);
        campaignRepository.save(campaign);
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

    public String computeWeekdayName(InGameDate date, CalendarConfig cfg) {
        int days = dateToEpochDays(date, cfg);
        int index = Math.floorMod(days - 1, cfg.weekdayNames().length);
        return cfg.weekdayNames()[index];
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

}
