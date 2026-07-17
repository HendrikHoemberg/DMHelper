package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestLinkRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.quest.packagev2.QuestSectionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestSectionAdapterTest {

    @Mock QuestRepository questRepo;
    @Mock QuestObjectiveRepository objectiveRepo;
    @Mock QuestLinkRepository linkRepo;

    private QuestSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new QuestSectionAdapter(questRepo, objectiveRepo, linkRepo,
                new dev.hendrikhoemberg.dmhelper.quest.service.QuestObjectiveDependencyValidator());
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder950() {
        assertThat(adapter.order()).isEqualTo(950);
    }

    @Test
    void sectionNameIsQuest() {
        assertThat(adapter.sectionName()).isEqualTo("Quest");
    }

    @Test
    void exportsQuests() {
        Quest q = new Quest();
        q.setId(UUID.randomUUID());
        q.setCampaign(campaign);
        q.setTitle("Find the Treasure");
        q.setStatus(dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus.ACTIVE);
        q.setSummary("A grand treasure hunt");
        q.setTags("main,treasure");
        q.setRewards("500 XP");
        q.setPrerequisites("Level 3");
        q.setOutcomeNotes("Treasure found!");
        q.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));

        when(questRepo.findByCampaignIdOrderByCreatedAtAscIdAsc(campaignId)).thenReturn(List.of(q));
        when(objectiveRepo.findByQuestIdOrderBySortOrderAsc(q.getId())).thenReturn(List.of());
        when(linkRepo.findByQuestIdOrderBySortOrderAsc(q.getId())).thenReturn(List.of());

        var keyService = new CampaignSectionAdapterTest.FakeKeyService();
        var ctx = new CampaignExportContext(
                campaignId, campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = new CampaignManifestAssembler();
        assembler.assets(List.of());
        adapter.exportSection(ctx, assembler);
        fillRest(assembler);
        var manifest = buildManifest(assembler);

        assertThat(manifest.quests()).hasSize(1);
        var qDto = manifest.quests().get(0);
        assertThat(qDto.title()).isEqualTo("Find the Treasure");
        assertThat(qDto.status()).isEqualTo("ACTIVE");
        assertThat(qDto.tags()).containsExactly("main", "treasure");
    }

    @Test
    void importsQuest() {
        var qDto = new CampaignManifestV2.QuestDto(
                "quest-treasure", "Treasure Quest", "ACTIVE", null, null,
                List.of("main"), null, null, null, null, null, Instant.parse("2025-01-01T00:00:00Z"));

        when(questRepo.save(any())).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, List.of(), List.of(qDto), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = new CampaignImportContext(
                campaignId, keys, new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);

        adapter.importSection(manifest, context);

        assertThat(keys.bindings)
                .containsValue(qDto.key());
    }

    private void fillRest(CampaignManifestAssembler a) {
        a.campaign(new CampaignManifestV2.CampaignDto("campaign-key", "test", null, null, null, null));
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
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
        a.diceRolls(List.of());
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler a) {
        return a.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
