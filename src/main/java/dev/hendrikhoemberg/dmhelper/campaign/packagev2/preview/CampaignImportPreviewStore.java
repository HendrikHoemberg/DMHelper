package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessReport;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.CampaignReadinessService;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.PreviewReadinessAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CampaignImportPreviewStore {

    private final ConcurrentHashMap<UUID, PendingCampaignImport> previews = new ConcurrentHashMap<>();
    private final PreviewReadinessAssembler assembler;
    private final CampaignReadinessService readinessService;
    private final Path stagingRoot;
    private final Clock clock;

    public CampaignImportPreviewStore() {
        this(null, null, Paths.get(System.getProperty("user.home"), ".dmhelper", "import-staging"), Clock.systemUTC());
    }

    public CampaignImportPreviewStore(PreviewReadinessAssembler assembler,
                                       CampaignReadinessService readinessService,
                                       Path stagingRoot, Clock clock) {
        this.assembler = assembler;
        this.readinessService = readinessService;
        this.stagingRoot = stagingRoot;
        this.clock = clock;
    }

    // test-only constructor
    CampaignImportPreviewStore(Path stagingRoot, Clock clock) {
        this(null, null, stagingRoot, clock);
    }

    @PostConstruct
    void cleanupAbandoned() {
        if (!Files.exists(stagingRoot)) return;
        try (var directories = Files.list(stagingRoot)) {
            directories.filter(Files::isDirectory).forEach(CampaignImportPreviewStore::deleteTree);
        } catch (IOException ignored) { }
    }

    public CampaignImportPreview retain(CampaignPackageValidationResult result) {
        expireStale();
        if (!result.valid() || result.manifest() == null) {
            result.stagedPackage().close();
            return preview(null, "BLOCKED", result, null);
        }
        UUID id = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(30));
        previews.put(id, new PendingCampaignImport(id, result, result.stagedPackage(), expiresAt));
        String status = result.problems().stream().anyMatch(p -> p.severity() == ImportSeverity.WARNING)
                ? "CONFIRM_WARNINGS" : "READY";
        return preview(id, status, result, expiresAt);
    }

    public PendingCampaignImport require(UUID previewId) {
        expireStale();
        PendingCampaignImport pending = previews.get(previewId);
        if (pending == null) throw new NoSuchElementException("Preview not found or expired");
        return pending;
    }

    public void discard(UUID previewId) {
        PendingCampaignImport pending = previews.remove(previewId);
        if (pending != null) pending.staging().close();
    }

    private void expireStale() {
        Instant now = clock.instant();
        previews.forEach((id, pending) -> {
            if (!pending.expiresAt().isAfter(now) && previews.remove(id, pending)) pending.staging().close();
        });
    }

    private CampaignImportPreview preview(UUID id, String status,
                                           CampaignPackageValidationResult result, Instant expiresAt) {
        CampaignManifestV2 manifest = result.manifest();
        long installed = manifest == null || manifest.assets() == null ? 0
                : manifest.assets().stream().mapToLong(a -> a.sizeBytes()).sum();
        int provenance = provenanceCount(manifest);
        int provenanceEligible = manifest == null ? 0 : size(manifest.customStatBlocks())
                + size(manifest.customSpells()) + size(manifest.customConditions())
                + size(manifest.customRules()) + size(manifest.customEquipment())
                + size(manifest.customMagicItems()) + size(manifest.customClasses())
                + size(manifest.customSpecies()) + size(manifest.customBackgrounds())
                + size(manifest.customFeats()) + size(manifest.adventures())
                + size(manifest.traps()) + size(manifest.hazards());
        CampaignReadinessReport readiness = manifest != null && assembler != null && readinessService != null
                ? readinessService.compute(assembler.fromManifest(manifest), Set.of())
                : new CampaignReadinessReport(List.of());
        return new CampaignImportPreview(id, status, result.sourceFormatVersion(),
                CampaignManifestV2.CURRENT_FORMAT_VERSION, counts(manifest),
                result.stagedPackage().uploadedBytes(), installed, provenance,
                Math.max(0, provenanceEligible - provenance), exclusions(manifest), result.migrations(),
                result.problems(), expiresAt, readiness);
    }

    private static CampaignEntityCounts counts(CampaignManifestV2 m) {
        if (m == null) return new CampaignEntityCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        int tokens = m.maps().stream().mapToInt(map -> size(map.tokens())).sum();
        int combatants = m.encounters().stream().mapToInt(encounter -> size(encounter.combatants())).sum();
        int chapters = m.adventures().stream().mapToInt(adventure -> size(adventure.chapters())).sum();
        int scenes = m.adventures().stream().flatMap(adventure -> adventure.chapters().stream())
                .mapToInt(chapter -> size(chapter.scenes())).sum();
        int combatLogEntries = m.encounters().stream().mapToInt(encounter -> size(encounter.combatLog())).sum();
        int noteLinks = m.notes().stream().mapToInt(note -> size(note.links())).sum();
        int sessionSceneVisits = m.session() != null ? size(m.session().sceneVisits()) : 0;
        return new CampaignEntityCounts(size(m.party()), size(m.customStatBlocks()),
                size(m.customSpells()), size(m.customConditions()), size(m.customRules()),
                size(m.customEquipment()), size(m.customMagicItems()), size(m.customClasses()),
                size(m.customSpecies()), size(m.customBackgrounds()), size(m.customFeats()),
                size(m.handouts()),
                size(m.maps()), tokens, size(m.encounters()), combatants, size(m.notes()), size(m.quickNotes()),
                size(m.assignments()), size(m.ledgerEntries()), size(m.timelineEvents()), size(m.adventures()),
                chapters, scenes, size(m.assets()), combatLogEntries, noteLinks,
                m.session() != null ? 1 : 0, sessionSceneVisits,
                size(m.traps()), size(m.hazards()));
    }

    private static int provenanceCount(CampaignManifestV2 m) {
        if (m == null) return 0;
        int count = 0;
        if (m.customStatBlocks() != null) count += (int) m.customStatBlocks().stream()
                .filter(s -> s.provenance() != null || (s.sourceKey() != null && !s.sourceKey().isBlank()))
                .count();
        if (m.customSpells() != null) count += (int) m.customSpells().stream().filter(s -> s.provenance() != null).count();
        if (m.customConditions() != null) count += (int) m.customConditions().stream().filter(s -> s.provenance() != null).count();
        if (m.customRules() != null) count += (int) m.customRules().stream().filter(s -> s.provenance() != null).count();
        if (m.customEquipment() != null) count += (int) m.customEquipment().stream().filter(s -> s.provenance() != null).count();
        if (m.customMagicItems() != null) count += (int) m.customMagicItems().stream().filter(s -> s.provenance() != null).count();
        if (m.customClasses() != null) count += (int) m.customClasses().stream().filter(s -> s.provenance() != null).count();
        if (m.customSpecies() != null) count += (int) m.customSpecies().stream().filter(s -> s.provenance() != null).count();
        if (m.customBackgrounds() != null) count += (int) m.customBackgrounds().stream().filter(s -> s.provenance() != null).count();
        if (m.customFeats() != null) count += (int) m.customFeats().stream().filter(s -> s.provenance() != null).count();
        if (m.adventures() != null) count += (int) m.adventures().stream().filter(a -> a.sourceAttribution() != null && !a.sourceAttribution().isBlank()).count();
        if (m.traps() != null) count += (int) m.traps().stream().filter(t -> t.provenance() != null).count();
        if (m.hazards() != null) count += (int) m.hazards().stream().filter(h -> h.provenance() != null).count();
        return count;
    }

    private static java.util.List<CampaignExportExclusion> exclusions(CampaignManifestV2 m) {
        return m == null || m.metadata() == null || m.metadata().exclusions() == null
                ? java.util.List.of() : m.metadata().exclusions();
    }

    private static int size(java.util.List<?> values) { return values == null ? 0 : values.size(); }

    private static void deleteTree(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
