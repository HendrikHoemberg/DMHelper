package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.calendar.packagev2.CalendarSectionAdapter;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarSectionAdapterTest {

    @Mock TimelineEventRepository timelineEventRepository;

    private CalendarSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new CalendarSectionAdapter(timelineEventRepository);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder1100() {
        assertThat(adapter.order()).isEqualTo(1100);
    }

    @Test
    void hasSectionNameCalendar() {
        assertThat(adapter.sectionName()).isEqualTo("Calendar");
    }

    @Test
    void exportsTimelineEvents() {
        var event = new TimelineEvent();
        event.setId(UUID.randomUUID());
        event.setCampaign(campaign);
        event.setInGameYear(1492);
        event.setInGameMonth(5);
        event.setInGameDay(1);
        event.setTitle("The Eclipse");
        event.setBody("A dark omen");

        when(timelineEventRepository.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAscIdAsc(campaignId))
                .thenReturn(List.of(event));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.timelineEvents()).hasSize(1);
        var dto = manifest.timelineEvents().get(0);
        assertThat(dto.inGameYear()).isEqualTo(1492);
        assertThat(dto.inGameMonth()).isEqualTo(5);
        assertThat(dto.inGameDay()).isEqualTo(1);
        assertThat(dto.title()).isEqualTo("The Eclipse");
        assertThat(dto.body()).isEqualTo("A dark omen");
    }

    @Test
    void exportsTimelineEventWithNoteRef() {
        var note = new Note();
        note.setId(UUID.randomUUID());
        note.setTitle("Secret Note");

        var event = new TimelineEvent();
        event.setId(UUID.randomUUID());
        event.setCampaign(campaign);
        event.setInGameYear(1500);
        event.setInGameMonth(0);
        event.setInGameDay(15);
        event.setTitle("Event");
        event.setNoteRef(note);

        when(timelineEventRepository.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAscIdAsc(campaignId))
                .thenReturn(List.of(event));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.timelineEvents()).hasSize(1);
        var dto = manifest.timelineEvents().get(0);
        assertThat(dto.noteRef()).isNotNull();
        assertThat(dto.noteRef().type().name()).isEqualTo("NOTE");
    }

    @Test
    void exportsEmptyListWhenNoEvents() {
        when(timelineEventRepository.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAscIdAsc(campaignId))
                .thenReturn(List.of());

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.timelineEvents()).isEmpty();
    }

    private CampaignExportContext exportContext() {
        return new CampaignExportContext(
                campaignId, campaign, CampaignExportOptions.complete(),
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
        a.notes(List.of());
        a.quickNotes(List.of());
        a.assignments(List.of());
        a.ledgerEntries(List.of());
        a.adventures(List.of());
        a.session(null);
        a.audioCues(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
