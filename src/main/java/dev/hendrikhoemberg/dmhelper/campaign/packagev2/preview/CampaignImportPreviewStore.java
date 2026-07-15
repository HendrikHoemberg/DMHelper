package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
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
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CampaignImportPreviewStore {

    private final ConcurrentHashMap<UUID, PendingCampaignImport> previews = new ConcurrentHashMap<>();
    private final Path stagingRoot;
    private final Clock clock;

    public CampaignImportPreviewStore() {
        this(Paths.get(System.getProperty("user.home"), ".dmhelper", "import-staging"), Clock.systemUTC());
    }

    public CampaignImportPreviewStore(Path stagingRoot, Clock clock) {
        this.stagingRoot = stagingRoot;
        this.clock = clock;
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

    private static CampaignImportPreview preview(UUID id, String status,
                                                 CampaignPackageValidationResult result, Instant expiresAt) {
        CampaignManifestV2 manifest = result.manifest();
        long installed = manifest == null || manifest.assets() == null ? 0
                : manifest.assets().stream().mapToLong(a -> a.sizeBytes()).sum();
        int provenance = provenanceCount(manifest);
        int provenanceEligible = manifest == null ? 0 : size(manifest.customStatBlocks()) + size(manifest.adventures());
        return new CampaignImportPreview(id, status, result.sourceFormatVersion(), 2, counts(manifest),
                result.stagedPackage().uploadedBytes(), installed, provenance,
                Math.max(0, provenanceEligible - provenance), exclusions(manifest), result.migrations(),
                result.problems(), expiresAt);
    }

    private static CampaignEntityCounts counts(CampaignManifestV2 m) {
        if (m == null) return new CampaignEntityCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        int tokens = m.maps().stream().mapToInt(map -> size(map.tokens())).sum();
        int combatants = m.encounters().stream().mapToInt(encounter -> size(encounter.combatants())).sum();
        int chapters = m.adventures().stream().mapToInt(adventure -> size(adventure.chapters())).sum();
        int scenes = m.adventures().stream().flatMap(adventure -> adventure.chapters().stream())
                .mapToInt(chapter -> size(chapter.scenes())).sum();
        return new CampaignEntityCounts(size(m.party()), size(m.customStatBlocks()), size(m.handouts()),
                size(m.maps()), tokens, size(m.encounters()), combatants, size(m.notes()), size(m.quickNotes()),
                size(m.assignments()), size(m.ledgerEntries()), size(m.timelineEvents()), size(m.adventures()),
                chapters, scenes, size(m.assets()));
    }

    private static int provenanceCount(CampaignManifestV2 m) {
        if (m == null) return 0;
        return (int) m.customStatBlocks().stream().filter(s -> s.sourceKey() != null && !s.sourceKey().isBlank()).count()
                + (int) m.adventures().stream().filter(a -> a.sourceAttribution() != null && !a.sourceAttribution().isBlank()).count();
    }

    private static java.util.List<String> exclusions(CampaignManifestV2 m) {
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
