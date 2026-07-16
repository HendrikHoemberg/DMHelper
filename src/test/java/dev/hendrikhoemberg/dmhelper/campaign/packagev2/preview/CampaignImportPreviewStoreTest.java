package dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.StagedCampaignPackage;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampaignImportPreviewStoreTest {

    @TempDir Path temp;

    @Test
    void expiresPreviewAfterThirtyMinutesAndDeletesStaging() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        Path root = temp.resolve("staging");
        Path stage = Files.createDirectories(root.resolve("one"));
        Path manifestPath = Files.writeString(stage.resolve("manifest.json"), "{}");
        var staged = new StagedCampaignPackage(stage, manifestPath, Map.of(), 2, 2,
                StagedCampaignPackage.ContainerKind.V2_JSON);
        var store = new CampaignImportPreviewStore(root, clock);
        var preview = store.retain(new CampaignPackageValidationResult(staged, minimal(), 2, Map.of(), List.of(), List.of()));

        clock.advance(Duration.ofMinutes(31));

        assertThatThrownBy(() -> store.require(preview.previewId())).isInstanceOf(NoSuchElementException.class);
        assertThat(stage).doesNotExist();
    }

    @Test
    void reportsAllTopLevelEntityCounts() throws Exception {
        Path root = temp.resolve("counts");
        Path stage = Files.createDirectories(root.resolve("one"));
        Path manifestPath = Files.writeString(stage.resolve("manifest.json"), "{}");
        var staged = new StagedCampaignPackage(stage, manifestPath, Map.of(), 2, 2,
                StagedCampaignPackage.ContainerKind.V2_JSON);
        CampaignManifestV2 manifest;
        try (var input = getClass().getResourceAsStream("/campaigns/v2/current-surface.dmcampaign/manifest.json")) {
            manifest = JsonMapper.builder().build().readValue(input, CampaignManifestV2.class);
        }

        var preview = new CampaignImportPreviewStore(root, Clock.systemUTC()).retain(
                new CampaignPackageValidationResult(staged, manifest, 2, Map.of(), List.of(), List.of()));

        assertThat(preview.counts().encounters()).isEqualTo(manifest.encounters().size());
        assertThat(preview.counts().notes()).isEqualTo(manifest.notes().size());
        assertThat(preview.counts().quickNotes()).isEqualTo(manifest.quickNotes().size());
        assertThat(preview.counts().assignments()).isEqualTo(manifest.assignments().size());
        assertThat(preview.counts().ledgerEntries()).isEqualTo(manifest.ledgerEntries().size());
        assertThat(preview.counts().timelineEvents()).isEqualTo(manifest.timelineEvents().size());
        assertThat(preview.counts().adventures()).isEqualTo(manifest.adventures().size());
        assertThat(preview.counts().chapters()).isEqualTo(1);
        assertThat(preview.counts().scenes()).isEqualTo(1);
        assertThat(preview.counts().combatLogEntries()).isEqualTo(0);
        assertThat(preview.counts().diceRolls()).isEqualTo(1);
        assertThat(preview.counts().noteLinks()).isEqualTo(1);
        assertThat(preview.counts().assets()).isEqualTo(manifest.assets().size());
        assertThat(preview.counts().partyMembers()).isEqualTo(manifest.party().size());
        assertThat(preview.counts().customStatBlocks()).isEqualTo(manifest.customStatBlocks().size());
        assertThat(preview.counts().handouts()).isEqualTo(manifest.handouts().size());
        assertThat(preview.counts().maps()).isEqualTo(manifest.maps().size());
        assertThat(preview.counts().tokens()).isEqualTo(1);
        assertThat(preview.counts().combatants()).isEqualTo(2);
    }

    private CampaignManifestV2 minimal() throws Exception {
        try (var input = getClass().getResourceAsStream("/campaigns/v2/minimal.dmcampaign.json")) {
            return JsonMapper.builder().build().readValue(input, CampaignManifestV2.class);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant value;
        private MutableClock(Instant value) { this.value = value; }
        void advance(Duration duration) { value = value.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }
}
