package dev.hendrikhoemberg.dmhelper.calendar.packagev2;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.TimelineEventDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CalendarSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final TimelineEventRepository timelineEventRepository;

    public CalendarSectionAdapter(TimelineEventRepository timelineEventRepository) {
        this.timelineEventRepository = timelineEventRepository;
    }

    @Override
    public String sectionName() {
        return "Calendar";
    }

    @Override
    public int order() {
        return 1100;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var events = timelineEventRepository.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAscIdAsc(
                context.campaignId());
        List<TimelineEventDto> dtos = events.stream()
                .map(e -> exportTimelineEvent(e, context))
                .toList();
        target.timelineEvents(dtos);
    }

    private TimelineEventDto exportTimelineEvent(TimelineEvent event, CampaignExportContext context) {
        String key = context.key(CampaignContentType.TIMELINE_EVENT, event.getId(), event.getTitle());

        ContentReference noteRef = null;
        if (event.getNoteRef() != null) {
            noteRef = context.packageRef(CampaignContentType.NOTE,
                    event.getNoteRef().getId(), event.getNoteRef().getTitle());
        }

        return new TimelineEventDto(key, event.getInGameYear(), event.getInGameMonth(), event.getInGameDay(),
                event.getTitle(), event.getBody(), noteRef);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<TimelineEventDto> dtos = source.timelineEvents();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (TimelineEventDto dto : dtos) {
            var event = new TimelineEvent();
            event.setCampaign(campaign);
            event.setInGameYear(dto.inGameYear());
            event.setInGameMonth(dto.inGameMonth());
            event.setInGameDay(dto.inGameDay());
            event.setTitle(dto.title());
            event.setBody(dto.body());

            if (dto.noteRef() != null) {
                ContentReference ref = dto.noteRef();
                context.defer("timeline note " + dto.key(), () -> {
                    var note = context.require(ref, CampaignContentType.NOTE, Note.class);
                    event.setNoteRef(note);
                });
            }

            timelineEventRepository.save(event);
            context.register(CampaignContentType.TIMELINE_EVENT, dto.key(), event, event.getId());
        }
    }
}
