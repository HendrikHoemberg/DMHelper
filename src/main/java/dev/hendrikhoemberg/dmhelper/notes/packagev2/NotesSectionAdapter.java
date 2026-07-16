package dev.hendrikhoemberg.dmhelper.notes.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.NoteDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.NoteLinkDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.QuickNoteDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteLink;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteLinkRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class NotesSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final NoteRepository noteRepository;
    private final NoteLinkRepository noteLinkRepository;
    private final QuickNoteRepository quickNoteRepository;

    public NotesSectionAdapter(NoteRepository noteRepository,
                                NoteLinkRepository noteLinkRepository,
                                QuickNoteRepository quickNoteRepository) {
        this.noteRepository = noteRepository;
        this.noteLinkRepository = noteLinkRepository;
        this.quickNoteRepository = quickNoteRepository;
    }

    @Override
    public String sectionName() {
        return "Notes";
    }

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var notes = noteRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(context.campaignId());
        List<NoteDto> noteDtos = notes.stream()
                .map(n -> exportNote(n, context))
                .toList();
        target.notes(noteDtos);

        var quickNotes = quickNoteRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(context.campaignId());
        List<QuickNoteDto> quickNoteDtos = quickNotes.stream()
                .map(qn -> exportQuickNote(qn, context))
                .toList();
        target.quickNotes(quickNoteDtos);
    }

    private NoteDto exportNote(Note note, CampaignExportContext context) {
        String key = context.key(CampaignContentType.NOTE, note.getId(), note.getTitle());
        var links = noteLinkRepository.findBySourceNoteIdOrderByIdAsc(note.getId());
        List<NoteLinkDto> linkDtos = links.stream()
                .map(link -> exportNoteLink(link, context))
                .toList();
        return new NoteDto(key, note.getType().name(), note.getTitle(),
                note.getBody(), note.getTags(), note.isDmOnly(),
                note.getCreatedAt(), linkDtos);
    }

    private NoteLinkDto exportNoteLink(NoteLink link, CampaignExportContext context) {
        CampaignContentType type = CampaignContentType.valueOf(link.getTargetType());
        ContentReference targetRef = context.packageRef(type, link.getTargetId(), link.getDisplayText());
        return new NoteLinkDto(link.getTargetType(), targetRef, link.getDisplayText(), link.isResolved());
    }

    private QuickNoteDto exportQuickNote(QuickNote qn, CampaignExportContext context) {
        String key = context.key(CampaignContentType.QUICK_NOTE, qn.getId(), qn.getBody());
        CampaignContentType type = CampaignContentType.valueOf(qn.getTargetType());
        ContentReference targetRef = context.packageRef(type, qn.getTargetId(), "quick-note-target");
        return new QuickNoteDto(key, targetRef, qn.getBody(), qn.getCreatedAt());
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<NoteDto> notes = source.notes();
        if (notes == null) return;

        var campaign = context.campaign();

        Map<String, Note> importedNotes = new LinkedHashMap<>();
        for (NoteDto dto : notes) {
            var note = new Note();
            note.setCampaign(campaign);
            note.setType(NoteType.valueOf(dto.type()));
            note.setTitle(dto.title());
            note.setBody(dto.body());
            note.setTags(dto.tags());
            note.setDmOnly(dto.dmOnly());
            if (dto.createdAt() != null) {
                note.setCreatedAt(dto.createdAt());
            }
            noteRepository.save(note);
            context.register(CampaignContentType.NOTE, dto.key(), note, note.getId());
            importedNotes.put(dto.key(), note);
        }

        List<NoteLink> pendingLinks = new ArrayList<>();
        List<Runnable> linkDeferred = new ArrayList<>();
        for (NoteDto dto : notes) {
            if (dto.links() == null) continue;
            Note note = importedNotes.get(dto.key());
            if (note == null) continue;
            for (NoteLinkDto linkDto : dto.links()) {
                NoteLink link = new NoteLink();
                link.setSourceNote(note);
                link.setTargetType(linkDto.targetType());
                link.setDisplayText(linkDto.displayText());
                link.setResolved(linkDto.resolved());
                noteLinkRepository.save(link);
                pendingLinks.add(link);

                if (linkDto.targetRef() != null) {
                    ContentReference ref = linkDto.targetRef();
                    CampaignContentType targetType = CampaignContentType.valueOf(linkDto.targetType());
                    linkDeferred.add(() -> {
                        Object resolved = context.require(ref, targetType, Object.class);
                        try {
                            UUID targetId = (UUID) resolved.getClass().getMethod("getId").invoke(resolved);
                            link.setTargetId(targetId);
                        } catch (Exception e) {
                            throw new RuntimeException("Cannot resolve ID for " + resolved.getClass(), e);
                        }
                    });
                }
            }
        }

        String description = "note link targets";
        context.defer(description, () -> {
            for (var task : linkDeferred) {
                task.run();
            }
        });

        List<QuickNoteDto> quickNoteDtos = source.quickNotes();
        if (quickNoteDtos == null) return;

        for (QuickNoteDto dto : quickNoteDtos) {
            var qn = new QuickNote();
            qn.setCampaign(campaign);
            qn.setBody(dto.body());
            if (dto.createdAt() != null) {
                qn.setCreatedAt(dto.createdAt());
            }
            context.defer("quick note " + dto.key() + " save", () -> {
                ContentReference ref = dto.targetRef();
                if (ref != null) {
                    Object resolved = context.require(ref, ref.type(), Object.class);
                    try {
                        UUID targetId = (UUID) resolved.getClass().getMethod("getId").invoke(resolved);
                        qn.setTargetType(ref.type().name());
                        qn.setTargetId(targetId);
                    } catch (Exception e) {
                        throw new RuntimeException("Cannot resolve ID for " + resolved.getClass(), e);
                    }
                }
                quickNoteRepository.save(qn);
                context.register(CampaignContentType.QUICK_NOTE, dto.key(), qn, qn.getId());
            });
        }
    }
}
