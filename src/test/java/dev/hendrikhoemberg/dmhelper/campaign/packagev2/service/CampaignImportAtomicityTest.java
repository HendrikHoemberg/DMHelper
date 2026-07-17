package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriteRequest;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportObserver;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionRegistry;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
class CampaignImportAtomicityTest {

    @Autowired CampaignPackageValidationPipeline pipeline;
    @Autowired CampaignImportPreviewStore previews;
    @Autowired CampaignImportCoordinator importer;
    @Autowired CampaignSectionRegistry registry;
    @Autowired CampaignRepository campaignsRepository;
    @Autowired CampaignPackageKeyRepository keysRepository;
    @Autowired CampaignService campaigns;
    @Autowired EntityManager entityManager;
    @MockitoBean CampaignImportObserver observer;
    @TempDir Path temp;

    private final AtomicInteger stagingSequence = new AtomicInteger();

    @Test
    void rollsBackRowsKeysAndFilesAfterEveryAdapterBoundaryAndKeepsPreviewRetryable() throws Exception {
        for (var section : registry.importers()) {
            var preview = retainFeatureCompleteFixture();
            long campaignsBefore = campaignsRepository.count();
            long keysBefore = keysRepository.count();
            Map<String, Long> rowsBefore = domainRowCounts();
            Set<String> filesBefore = finalHandoutFiles();

            doThrow(new ForcedImportFailure(section.sectionName()))
                    .when(observer).afterSection(section.sectionName());

            assertThatThrownBy(() -> importer.confirm(preview.previewId(), true))
                    .as(section.sectionName())
                    .isInstanceOf(ForcedImportFailure.class);
            assertRolledBack(campaignsBefore, keysBefore, rowsBefore, filesBefore, section.sectionName());
            assertThatCode(() -> previews.require(preview.previewId()))
                    .as(section.sectionName() + " preview")
                    .doesNotThrowAnyException();

            reset(observer);
            var retried = importer.confirm(preview.previewId(), true);
            campaigns.delete(retried.getId());
        }
    }

    @Test
    void rollsBackWhenADeferredSetterFailsAndKeepsPreviewRetryable() throws Exception {
        var preview = retainFeatureCompleteFixture();
        long campaignsBefore = campaignsRepository.count();
        long keysBefore = keysRepository.count();
        Map<String, Long> rowsBefore = domainRowCounts();
        Set<String> filesBefore = finalHandoutFiles();
        doThrow(new ForcedImportFailure("deferred"))
                .when(observer).beforeDeferredSetter(anyString());

        assertThatThrownBy(() -> importer.confirm(preview.previewId(), true))
                .isInstanceOf(ForcedImportFailure.class);
        assertRolledBack(campaignsBefore, keysBefore, rowsBefore, filesBefore, "deferred setters");
        assertThatCode(() -> previews.require(preview.previewId())).doesNotThrowAnyException();

        reset(observer);
        var retried = importer.confirm(preview.previewId(), true);
        campaigns.delete(retried.getId());
    }

    private void assertRolledBack(long campaignsBefore, long keysBefore,
                                  Map<String, Long> rowsBefore,
                                  Set<String> filesBefore, String boundary) throws Exception {
        assertThat(campaignsRepository.count()).as(boundary + " campaign rows").isEqualTo(campaignsBefore);
        assertThat(keysRepository.count()).as(boundary + " package-key rows").isEqualTo(keysBefore);
        assertThat(domainRowCounts()).as(boundary + " domain rows").isEqualTo(rowsBefore);
        assertThat(finalHandoutFiles()).as(boundary + " handout files").isEqualTo(filesBefore);
    }

    private Map<String, Long> domainRowCounts() {
        List<String> tables = List.of(
                "campaign", "party_member", "character_sheet", "sheet_resource", "sheet_spell_ref",
                "stat_block", "game_map", "token", "handout", "encounter", "combatant",
                "combat_log_entry", "item_assignment", "ledger_entry", "timeline_event", "note",
                "note_link", "quick_note", "adventure", "adventure_chapter", "adventure_scene",
                "scene_statblock", "scene_handout", "scene_section", "scene_check", "scene_participant",
                "scene_transition", "scene_link", "dice_roll", "campaign_package_key",
                "quest", "quest_objective", "quest_link", "quest_objective_dependency",
                "source_annotation", "session_objective_change");
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tables) {
            Number count = (Number) entityManager.createNativeQuery("select count(*) from " + table)
                    .getSingleResult();
            counts.put(table, count.longValue());
        }
        return counts;
    }

    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreview
    retainFeatureCompleteFixture() throws Exception {
        var mapper = JsonMapper.builder().build();
        CampaignManifestV2 manifest;
        try (var input = new ClassPathResource(
                "campaigns/v2/feature-complete.dmcampaign/manifest.json").getInputStream()) {
            manifest = mapper.readValue(input, CampaignManifestV2.class);
        }
        Map<String, InputStreamSource> assets = new LinkedHashMap<>();
        for (var descriptor : manifest.assets()) {
            byte[] bytes;
            try (var input = new ClassPathResource(
                    "campaigns/v2/feature-complete.dmcampaign/" + descriptor.path()).getInputStream()) {
                bytes = input.readAllBytes();
            }
            assets.put(descriptor.key(), new ByteArrayResource(bytes));
        }
        var output = new ByteArrayOutputStream();
        new CampaignPackageWriter().write(new CampaignPackageWriteRequest(
                "feature-complete.dmcampaign", manifest, assets), output);
        var reader = new CampaignPackageReader(
                temp.resolve("staging-" + stagingSequence.incrementAndGet()));
        var staged = reader.read(new ByteArrayInputStream(output.toByteArray()),
                "feature-complete.dmcampaign", "application/vnd.dmhelper.campaign+zip");
        var result = pipeline.validate(staged);
        assertThat(result.valid()).as(result.problems().toString()).isTrue();
        return previews.retain(result);
    }

    private Set<String> finalHandoutFiles() throws Exception {
        Path directory = Path.of(System.getProperty("user.home"), ".dmhelper", "files");
        if (!Files.isDirectory(directory)) return Set.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private static final class ForcedImportFailure extends RuntimeException {
        private ForcedImportFailure(String boundary) {
            super(boundary);
        }
    }
}
