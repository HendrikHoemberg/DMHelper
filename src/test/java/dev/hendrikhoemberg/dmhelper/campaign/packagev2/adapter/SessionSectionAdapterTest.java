package dev.hendrikhoemberg.dmhelper.campaign.packagev2.adapter;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.Metadata;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SessionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SessionSceneVisitDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignAssetCollector;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.service.CampaignExportOptions;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSession;
import dev.hendrikhoemberg.dmhelper.session.data.CampaignSessionRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionObjectiveChangeRepository;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisit;
import dev.hendrikhoemberg.dmhelper.session.data.SessionSceneVisitRepository;
import dev.hendrikhoemberg.dmhelper.session.packagev2.SessionSectionAdapter;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionSectionAdapterTest {

    @Mock CampaignSessionRepository sessionRepository;
    @Mock SessionSceneVisitRepository visitRepository;
    @Mock SessionObjectiveChangeRepository objectiveChangeRepository;

    private SessionSectionAdapter adapter;
    private Campaign campaign;
    private UUID campaignId;

    @BeforeEach
    void setUp() {
        adapter = new SessionSectionAdapter(sessionRepository, visitRepository, objectiveChangeRepository);
        campaign = new Campaign();
        campaignId = UUID.randomUUID();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
    }

    @Test
    void hasOrder1150() {
        assertThat(adapter.order()).isEqualTo(1150);
    }

    @Test
    void hasSectionNameSession() {
        assertThat(adapter.sectionName()).isEqualTo("Session");
    }

    @Test
    void exportsNullWhenNoSession() {
        when(sessionRepository.findByCampaignId(campaignId)).thenReturn(Optional.empty());

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.session()).isNull();
    }

    @Test
    void exportsNullWhenSessionIdle() {
        CampaignSession session = CampaignSession.idle(campaign);
        when(sessionRepository.findByCampaignId(campaignId)).thenReturn(Optional.of(session));

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.session()).isNull();
    }

    @Test
    void exportsRunningSession() {
        CampaignSession session = CampaignSession.idle(campaign);
        session.setStatus(CampaignSession.Status.RUNNING);
        session.setStartedAt(Instant.parse("2025-07-16T18:00:00Z"));
        session.setPresentationMode(CampaignSession.PresentationMode.CURTAIN);
        UUID sessionId = UUID.randomUUID();
        session.setId(sessionId);
        when(sessionRepository.findByCampaignId(campaignId)).thenReturn(Optional.of(session));
        when(visitRepository.findBySessionIdOrderByVisitedAtAscIdAsc(sessionId)).thenReturn(List.of());

        var ctx = exportContext();
        var assembler = assembler();

        adapter.exportSection(ctx, assembler);

        var manifest = buildManifest(assembler);
        assertThat(manifest.session()).isNotNull();
        assertThat(manifest.session().status()).isEqualTo("RUNNING");
        assertThat(manifest.session().presentationMode()).isEqualTo("CURTAIN");
        assertThat(manifest.session().attendeeRefs()).isEmpty();
        assertThat(manifest.session().sceneVisits()).isEmpty();
    }

    @Test
    void importCreatesSessionWithCorrectStatus() {
        var sessionId = UUID.randomUUID();
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            var s = invocation.getArgument(0, CampaignSession.class);
            s.setId(sessionId);
            return s;
        });
        var dto = new SessionDto("session", "RUNNING",
                Instant.parse("2025-07-16T18:00:00Z"), null, null,
                null, null, null, "CURTAIN",
                null, List.of(), List.of(), null, null);
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, dto, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = new CampaignImportContext(
                campaignId, keys, new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);

        adapter.importSection(manifest, context);

        assertThat(keys.bindings)
                .containsEntry(CampaignContentType.SESSION.name() + ":" + sessionId, "session");
    }

    @Test
    void importDoesNotRestoreDmOnlyHandoutToPlayerPresentation() {
        var sessionId = UUID.randomUUID();
        AtomicReference<CampaignSession> imported = new AtomicReference<>();
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            var saved = invocation.getArgument(0, CampaignSession.class);
            saved.setId(sessionId);
            imported.set(saved);
            return saved;
        });
        Handout secret = new Handout();
        secret.setId(UUID.randomUUID());
        secret.setCampaign(campaign);
        secret.setDmOnly(true);
        ContentReference secretRef = ContentReference.packageRef(CampaignContentType.HANDOUT, "secret");
        var dto = new SessionDto("session", "PAUSED",
                Instant.parse("2025-07-16T18:00:00Z"), null, null,
                null, null, null, "HANDOUT",
                secretRef, List.of(), List.of(), null, null);
        var manifest = new CampaignManifestV2(
                2, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, dto, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var keys = new CampaignSectionAdapterTest.FakeKeyService();
        var context = new CampaignImportContext(
                campaignId, keys, new PendingCampaignImport(UUID.randomUUID(), null, null, null));
        context.setCampaign(campaign);
        context.register(CampaignContentType.HANDOUT, "secret", secret, secret.getId());

        adapter.importSection(manifest, context);
        context.runDeferred();

        assertThat(imported.get().getPresentationMode()).isEqualTo(CampaignSession.PresentationMode.CURTAIN);
        assertThat(imported.get().getPresentedHandout()).isNull();
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
        a.timelineEvents(List.of());
        a.adventures(List.of());
        a.session(null);
        a.diceRolls(List.of());
        a.audioCues(List.of());
        return a;
    }

    private CampaignManifestV2 buildManifest(CampaignManifestAssembler assembler) {
        return assembler.build(new Metadata("pkg-key", null, "test", null, null, List.of()));
    }
}
