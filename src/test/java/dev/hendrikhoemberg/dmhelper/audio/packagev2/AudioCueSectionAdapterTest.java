package dev.hendrikhoemberg.dmhelper.audio.packagev2;

import dev.hendrikhoemberg.dmhelper.audio.data.AudioCue;
import dev.hendrikhoemberg.dmhelper.audio.data.AudioCueRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AudioCueDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AudioCueSectionAdapterTest {

    private AudioCueSectionAdapter adapter;

    @Mock
    private AudioCueRepository repository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        adapter = new AudioCueSectionAdapter(repository);
        campaign = new Campaign();
        campaign.setId(UUID.randomUUID());
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder180() {
        assertThat(adapter.order()).isEqualTo(180);
    }

    @Test
    void exportsAudioCues() {
        var cue = createCue("my-cue");
        when(repository.findByCampaignIdOrderByNameAsc(campaign.getId())).thenReturn(List.of(cue));

        var keyService = new FakeKeyService();
        var ctx = new CampaignExportContext(
                campaign.getId(), campaign, CampaignExportOptions.complete(),
                keyService, new CampaignAssetCollector());
        var assembler = minimalAssembler();
        adapter.exportSection(ctx, assembler);

        var manifest = assembler.build(new Metadata("pkg", null, "test", null, null, List.of()));
        assertThat(manifest.audioCues()).hasSize(1);
        var dto = manifest.audioCues().get(0);
        assertThat(dto.name()).isEqualTo("Battle Theme");
        assertThat(dto.referenceKind()).isEqualTo("VIDEO");
        assertThat(dto.category()).isEqualTo("COMBAT");
        assertThat(dto.transitionPreference()).isEqualTo("CROSSFADE");
    }

    @Test
    void importsAudioCues() {
        var keyService = new FakeKeyService();
        var context = new CampaignImportContext(
                UUID.randomUUID(), keyService, pendingImport());
        context.setCampaign(campaign);

        var manifest = new CampaignManifestV2(
                2, null,
                new CampaignManifestV2.CampaignDto("campaign-key", "Test", "desc",
                        Instant.now(), null, null, null),
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                List.of(new AudioCueDto("cue-key", "Imported Cue", null, "VIDEO",
                        "https://example.com/track", "My Track", "Artist",
                        null, 120, "AMBIENT", 75, "CROSSFADE", "Some notes")));

        adapter.importSection(manifest, context);

        assertThat(context.campaign()).isNotNull();
    }

    @Test
    void preservesOpaqueUnsupportedProviderId() {
        var keyService = new FakeKeyService();
        var context = new CampaignImportContext(
                UUID.randomUUID(), keyService, pendingImport());
        context.setCampaign(campaign);

        var manifest = new CampaignManifestV2(
                2, null,
                new CampaignManifestV2.CampaignDto("campaign-key", "Test", "desc",
                        Instant.now(), null, null, null),
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(),
                List.of(new AudioCueDto("cue-key", "Cue", "unknown-provider", "VIDEO",
                        "ref123", null, null, null, null, "CUSTOM", null, "CROSSFADE", null)));

        adapter.importSection(manifest, context);

        assertThat(context.campaign()).isNotNull();
    }

    @Test
    void importsEmptyAudioCuesAsEmptyList() {
        var manifest = new CampaignManifestV2(
                2, null,
                new CampaignManifestV2.CampaignDto("campaign-key", "Test", "desc",
                        Instant.now(), null, null, null),
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, null, null, null, null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());

        assertThat(manifest.audioCues()).isEmpty();
    }

    private static AudioCue createCue(String cueKey) {
        var cue = new AudioCue();
        cue.setId(UUID.randomUUID());
        cue.setCampaign(new Campaign());
        cue.setCueKey(cueKey);
        cue.setName("Battle Theme");
        cue.setProviderId("youtube");
        cue.setReferenceKind(dev.hendrikhoemberg.dmhelper.audio.data.AudioReferenceKind.VIDEO);
        cue.setProviderReference("dQw4w9WgXcQ");
        cue.setCachedTitle("Rick Astley - Never Gonna Give You Up");
        cue.setArtistOrOwner("Rick Astley");
        cue.setDurationSeconds(212);
        cue.setCategory(dev.hendrikhoemberg.dmhelper.audio.data.AudioCategory.COMBAT);
        cue.setVolumeHint(80);
        cue.setTransitionPreference(dev.hendrikhoemberg.dmhelper.audio.data.AudioTransitionPreference.CROSSFADE);
        cue.setNotes("Test notes");
        return cue;
    }

    private static CampaignManifestAssembler minimalAssembler() {
        var a = new CampaignManifestAssembler();
        a.campaign(new CampaignManifestV2.CampaignDto("campaign", "Test", "desc",
                Instant.now(), null, null, null));
        a.assets(List.of());
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
        a.quests(List.of());
        a.annotations(List.of());
        a.worldNpcs(List.of());
        a.worldLocations(List.of());
        a.factions(List.of());
        a.worldRelationships(List.of());
        a.factionClocks(List.of());
        a.rollableTables(List.of());
        a.traps(List.of());
        a.hazards(List.of());
        return a;
    }

    private static PendingCampaignImport pendingImport() {
        return new PendingCampaignImport(UUID.randomUUID(), null, null, null);
    }

    public static class FakeKeyService extends CampaignPackageKeyService {
        final Map<String, String> bindings = new LinkedHashMap<>();

        public FakeKeyService() {
            super(null);
        }

        @Override
        public String getOrCreate(UUID campaignId, CampaignContentType type, UUID entityId, String displayName) {
            String mapKey = type.name() + ":" + entityId;
            if (bindings.containsKey(mapKey)) {
                return bindings.get(mapKey);
            }
            String slug = displayName.isBlank() ? "item"
                    : displayName.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
            String key = slug + "-" + entityId.toString().substring(0, 8);
            bindings.put(mapKey, key);
            return key;
        }

        @Override
        public void bindImported(UUID campaignId, CampaignContentType type, UUID entityId, String packageKey) {
            String mapKey = type.name() + ":" + entityId;
            if (bindings.containsKey(mapKey)) {
                throw new IllegalArgumentException("Entity already bound");
            }
            bindings.put(mapKey, packageKey);
        }

        @Override
        public java.util.Optional<String> find(UUID campaignId, CampaignContentType type, UUID entityId) {
            return java.util.Optional.empty();
        }
    }
}
