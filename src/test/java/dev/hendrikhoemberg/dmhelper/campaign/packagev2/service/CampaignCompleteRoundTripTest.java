package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageReader;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriteRequest;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageWriter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.CampaignImportPreviewStore;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationPipeline;
import dev.hendrikhoemberg.dmhelper.campaign.service.CampaignService;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CampaignCompleteRoundTripTest {

    @Autowired CampaignPackageValidationPipeline pipeline;
    @Autowired CampaignImportPreviewStore previews;
    @Autowired CampaignImportCoordinator importer;
    @Autowired CampaignExportCoordinator exporter;
    @Autowired CampaignSemanticSnapshotService snapshots;
    @Autowired CampaignService campaigns;
    @TempDir Path temp;

    private final JsonMapper mapper = JsonMapper.builder().build();

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("minimal", "campaigns/v2/minimal.dmcampaign.json", null),
                Arguments.of("feature-complete", "campaigns/v2/feature-complete.dmcampaign/manifest.json",
                        "campaigns/v2/feature-complete.dmcampaign/"),
                Arguments.of("published-adventure", "campaigns/v2/published-adventure-shaped.dmcampaign/manifest.json",
                        "campaigns/v2/published-adventure-shaped.dmcampaign/"),
                Arguments.of("structured-adventure-quest", "campaigns/v2/structured-adventure-quest.dmcampaign/manifest.json",
                        "campaigns/v2/structured-adventure-quest.dmcampaign/")
        );
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("fixtures")
    void fixtureSurvivesImportExportImportWithoutSemanticLoss(
            String label, String manifestPath, String assetRoot) throws Exception {
        CampaignManifestV2 source = readManifest(manifestPath);
        byte[] packageBytes = packageBytes(label, source, assetRoot);

        var firstResult = validate(label + "-first", packageBytes, !source.assets().isEmpty());
        assertThat(firstResult.valid()).as(firstResult.problems().toString()).isTrue();
        var campaignA = importer.confirm(previews.retain(firstResult).previewId(), true);

        CampaignSemanticSnapshot sourceSnapshot = CampaignSemanticSnapshot.from(source);
        CampaignSemanticSnapshot snapshotA = snapshots.snapshot(campaignA.getId());
        CampaignSemanticComparator.assertEquivalent(sourceSnapshot, snapshotA);

        CampaignPackageArtifact artifact = exporter.export(campaignA.getId());
        assertThat(artifact.manifest().metadata().exclusions()).isEmpty();
        byte[] exportedBytes = write(artifact.writeRequest());

        var secondResult = validate(label + "-second", exportedBytes, artifact.zipped());
        assertThat(secondResult.valid()).as(secondResult.problems().toString()).isTrue();
        var campaignB = importer.confirm(previews.retain(secondResult).previewId(), true);
        CampaignSemanticSnapshot snapshotB = snapshots.snapshot(campaignB.getId());

        CampaignSemanticComparator.assertEquivalent(snapshotA, snapshotB);

        campaigns.delete(campaignA.getId());
        campaigns.delete(campaignB.getId());
    }

    @ParameterizedTest(name = "combatLog={0}, diceHistory={1}")
    @CsvSource({"false,true", "true,false", "false,false"})
    void explicitHistoryOptOutsRemoveOnlyTheSelectedHistory(
            boolean includeCombatLog, boolean includeDiceHistory) throws Exception {
        CampaignManifestV2 source = readManifest("campaigns/v2/feature-complete.dmcampaign/manifest.json");
        byte[] packageBytes = packageBytes(
                "feature-complete-opt-out", source, "campaigns/v2/feature-complete.dmcampaign/");
        var validated = validate("feature-complete-opt-out", packageBytes, true);
        assertThat(validated.valid()).as(validated.problems().toString()).isTrue();
        var campaign = importer.confirm(previews.retain(validated).previewId(), true);

        var complete = exporter.export(campaign.getId(), CampaignExportOptions.complete()).manifest();
        var opted = exporter.export(campaign.getId(),
                new CampaignExportOptions(includeCombatLog, includeDiceHistory)).manifest();
        List<CampaignExportExclusion> exclusions = new CampaignExportOptions(
                includeCombatLog, includeDiceHistory).exclusions();

        assertThat(opted.metadata().exclusions()).containsExactlyElementsOf(exclusions);
        assertThat(opted.encounters()).allSatisfy(encounter -> {
            if (!includeCombatLog) assertThat(encounter.combatLog()).isEmpty();
        });
        if (!includeDiceHistory) assertThat(opted.diceRolls()).isEmpty();

        CampaignSemanticComparator.assertEquivalent(
                CampaignSemanticSnapshot.from(withHistorySelection(
                        complete, includeCombatLog, includeDiceHistory, exclusions)),
                CampaignSemanticSnapshot.from(opted));
        campaigns.delete(campaign.getId());
    }

    private CampaignManifestV2 withHistorySelection(
            CampaignManifestV2 source, boolean includeCombatLog, boolean includeDiceHistory,
            List<CampaignExportExclusion> exclusions) {
        var encounters = source.encounters().stream()
                .map(encounter -> new CampaignManifestV2.EncounterDto(
                        encounter.key(), encounter.name(), encounter.combatants(), encounter.status(),
                        encounter.round(), encounter.activeTurnIndex(), encounter.logSequence(),
                        encounter.lairActionName(), encounter.lairActionDescription(), encounter.mapRef(),
                        encounter.lairActionTriggered(),
                        includeCombatLog ? encounter.combatLog() : List.of()))
                .toList();
        var metadata = new CampaignManifestV2.Metadata(
                source.metadata().packageKey(), source.metadata().createdAt(), source.metadata().generator(),
                source.metadata().catalogVersion(), source.metadata().catalogSha256(), exclusions);
        return new CampaignManifestV2(
                source.formatVersion(), metadata, source.campaign(), source.assets(), source.party(),
                source.customStatBlocks(), source.customSpells(), source.customConditions(), source.customRules(),
                source.customEquipment(), source.customMagicItems(), source.customClasses(), source.customSpecies(),
                source.customBackgrounds(), source.customFeats(),
                source.handouts(), source.maps(), encounters, source.notes(),
                source.quickNotes(), source.assignments(), source.ledgerEntries(), source.timelineEvents(),
                source.adventures(), source.session(), includeDiceHistory ? source.diceRolls() : List.of(),
                source.quests(), source.annotations());
    }

    private CampaignManifestV2 readManifest(String path) throws Exception {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(input, CampaignManifestV2.class);
        }
    }

    private byte[] packageBytes(String label, CampaignManifestV2 manifest, String assetRoot) throws Exception {
        Map<String, InputStreamSource> sources = new LinkedHashMap<>();
        if (assetRoot != null) {
            for (var descriptor : manifest.assets()) {
                byte[] bytes;
                try (var input = new ClassPathResource(assetRoot + descriptor.path()).getInputStream()) {
                    bytes = input.readAllBytes();
                }
                sources.put(descriptor.key(), new ByteArrayResource(bytes));
            }
        }
        return write(new CampaignPackageWriteRequest(
                label + (sources.isEmpty() ? ".dmcampaign.json" : ".dmcampaign"), manifest, sources));
    }

    private byte[] write(CampaignPackageWriteRequest request) throws Exception {
        var output = new ByteArrayOutputStream();
        new CampaignPackageWriter().write(request, output);
        return output.toByteArray();
    }

    private dev.hendrikhoemberg.dmhelper.campaign.packagev2.validation.CampaignPackageValidationResult validate(
            String label, byte[] bytes, boolean zipped) throws Exception {
        var reader = new CampaignPackageReader(temp.resolve(label));
        var staged = reader.read(new ByteArrayInputStream(bytes),
                label + (zipped ? ".dmcampaign" : ".dmcampaign.json"),
                zipped ? "application/vnd.dmhelper.campaign+zip" : "application/json");
        return pipeline.validate(staged);
    }
}
