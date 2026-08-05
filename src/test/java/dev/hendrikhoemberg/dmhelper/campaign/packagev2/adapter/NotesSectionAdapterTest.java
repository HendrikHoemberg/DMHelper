package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteLink;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteLinkRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.packagev2.NotesSectionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotesSectionAdapterTest {

    @Mock NoteRepository noteRepository;
    @Mock NoteLinkRepository noteLinkRepository;
    @Mock QuickNoteRepository quickNoteRepository;

    private NotesSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new NotesSectionAdapter(noteRepository, noteLinkRepository, quickNoteRepository);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder1000() {
        assertThat(adapter.order()).isEqualTo(1000);
    }

    @Test
    void hasSectionNameNotes() {
        assertThat(adapter.sectionName()).isEqualTo("Notes");
    }

    @Test
    void exportsNotesAndQuickNotes() {
        var note = new Note();
        note.setId(UUID.randomUUID());
        note.setCampaign(campaign);
        note.setType(NoteType.LOCATION);
        note.setTitle("Secret Cave");
        note.setBody("Hidden entrance");
        note.setTags("secret,cave");
        note.setDmOnly(true);
        note.setCreatedAt(Instant.parse("2025-06-01T12:00:00Z"));

        var link = new NoteLink();
        link.setId(UUID.randomUUID());
        link.setSourceNote(note);
        link.setTargetType("NOTE");
        link.setTargetId(UUID.randomUUID());
        link.setDisplayText("Linked Note");
        link.setResolved(true);

        var qn = new QuickNote();
        qn.setId(UUID.randomUUID());
        qn.setCampaign(campaign);
        qn.setTargetType("NOTE");
        qn.setTargetId(UUID.randomUUID());
        qn.setBody("Quick body");
        qn.setCreatedAt(Instant.parse("2025-06-02T12:00:00Z"));

        when(noteRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId))
                .thenReturn(List.of(note));
        when(noteLinkRepository.findBySourceNoteIdOrderByIdAsc(note.getId()))
                .thenReturn(List.of(link));
        when(quickNoteRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId))
                .thenReturn(List.of(qn));

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.notes()).hasSize(1);
        assertThat(manifest.quickNotes()).hasSize(1);

        var noteDto = manifest.notes().get(0);
        assertThat(noteDto.title()).isEqualTo("Secret Cave");
        assertThat(noteDto.type()).isEqualTo("LOCATION");
        assertThat(noteDto.dmOnly()).isTrue();
        assertThat(noteDto.createdAt()).isEqualTo(note.getCreatedAt());
        assertThat(noteDto.links()).hasSize(1);
        assertThat(noteDto.links().get(0).displayText()).isEqualTo("Linked Note");
        assertThat(noteDto.links().get(0).resolved()).isTrue();

        var qnDto = manifest.quickNotes().get(0);
        assertThat(qnDto.body()).isEqualTo("Quick body");
        assertThat(qnDto.createdAt()).isEqualTo(qn.getCreatedAt());
    }

    @Test
    void exportsEmptyListsWhenNoNotes() {
        when(noteRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId))
                .thenReturn(List.of());
        when(quickNoteRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId))
                .thenReturn(List.of());

        var ctx = exportContext(CampaignExportOptions.complete());
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.notes()).isEmpty();
        assertThat(manifest.quickNotes()).isEmpty();
    }

    private CampaignExportContext exportContext(CampaignExportOptions options) {
        return new CampaignExportContext(
                campaignId, campaign, options,
                new CampaignSectionAdapterTest.FakeKeyService(),
                new CampaignAssetCollector());
    }

    private CampaignManifestAssembler assembler() {
        var a = new CampaignManifestAssembler();
        a.assets(List.of());
        a.campaign(new CampaignManifestV2.CampaignDto("key", "test", null, null, null, null, null));
        a.party(List.of());
        a.customStatBlocks(List.of());
        a.customSpells(List.of());
        a.customConditions(List.of());
        a.customRules(List.of());
        a.customEquipment(List.of());
        a.customMagicItems(List.of());
        a.customClasses(List.of());
        a.customSpecies(List.of());
        a.customBackgrounds(List.of());
        a.customFeats(List.of());
        a.handouts(List.of());
        a.maps(List.of());
        a.encounters(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
        a.audioCues(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }
}
