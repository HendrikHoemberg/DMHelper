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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CampaignImportPreviewStore {

    private final ConcurrentHashMap<UUID, PendingCampaignImport> previews = new ConcurrentHashMap<>();
    private final Path stagingRoot;
    private final Clock clock;

    public CampaignImportPreviewStore() {
        this.stagingRoot = Paths.get(System.getProperty("user.home"), ".dmhelper", "import-staging");
        this.clock = Clock.systemUTC();
    }

    @PostConstruct
    void cleanupAbandoned() {
        if (Files.exists(stagingRoot)) {
            try (var dirs = Files.list(stagingRoot)) {
                dirs.filter(Files::isDirectory).forEach(dir -> {
                    try (var walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                                });
                    } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
        }
    }

    public CampaignImportPreview retain(CampaignPackageValidationResult result) {
        expireStale();
        if (!result.valid()) {
            result.stagedPackage().close();
            return new CampaignImportPreview(null, "BLOCKED",
                    result.sourceFormatVersion(), 2,
                    countsFor(result.manifest()), 0, 0, 0, 0,
                    exclusionsFor(result.manifest()),
                    result.migrations(), result.problems(), null);
        }
        UUID previewId = UUID.randomUUID();
        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(30));
        var pending = new PendingCampaignImport(previewId, result, result.stagedPackage());
        previews.put(previewId, pending);

        String status = result.problems().stream()
                .anyMatch(p -> p.severity() == ImportSeverity.WARNING) ? "CONFIRM_WARNINGS" : "READY";
        return new CampaignImportPreview(previewId, status,
                result.sourceFormatVersion(), 2,
                countsFor(result.manifest()),
                result.stagedPackage().uploadedBytes(),
                result.stagedPackage().expandedBytes(),
                0, 0,
                exclusionsFor(result.manifest()),
                result.migrations(), result.problems(), expiresAt);
    }

    public PendingCampaignImport require(UUID previewId) {
        expireStale();
        PendingCampaignImport pending = previews.get(previewId);
        if (pending == null) {
            throw new IllegalStateException("Preview not found or expired: " + previewId);
        }
        return pending;
    }

    public void discard(UUID previewId) {
        PendingCampaignImport pending = previews.remove(previewId);
        if (pending != null) {
            pending.staging().close();
        }
    }

    private void expireStale() {
        Instant now = clock.instant();
        previews.entrySet().removeIf(entry -> {
            if (entry.getValue().result() != null) {
                return false;
            }
            entry.getValue().staging().close();
            return true;
        });
    }

    private static CampaignEntityCounts countsFor(CampaignManifestV2 m) {
        if (m == null) return new CampaignEntityCounts(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);
        return new CampaignEntityCounts(
                m.party() != null ? m.party().size() : 0,
                m.customStatBlocks() != null ? m.customStatBlocks().size() : 0,
                m.handouts() != null ? m.handouts().size() : 0,
                m.maps() != null ? m.maps().size() : 0,
                countTokens(m), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                m.assets() != null ? m.assets().size() : 0);
    }

    private static int countTokens(CampaignManifestV2 m) {
        if (m.maps() == null) return 0;
        return m.maps().stream()
                .mapToInt(map -> map.tokens() != null ? map.tokens().size() : 0)
                .sum();
    }

    private static java.util.List<String> exclusionsFor(CampaignManifestV2 m) {
        if (m == null || m.metadata() == null) return java.util.List.of();
        return m.metadata().exclusions() != null ? m.metadata().exclusions() : java.util.List.of();
    }
}
