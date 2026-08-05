package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AdventureDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ChapterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ConversionOmissionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneParticipantDto;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewReadinessAssemblerTest {

    private final PreviewReadinessAssembler assembler = new PreviewReadinessAssembler();

    @Test
    void hostileSceneWithoutEncounterIsBlocker() {
        var goblin = new SceneParticipantDto("Goblin", 1, "HOSTILE", null, null, null, null, null, 1);
        var scene = new SceneDto("s1", "Ambush", null, "UNVISITED", 1,
                null, null, null, null, null,
                null, null, null, null, null, null, List.of(goblin), null, null, null);
        var inputs = assembler.fromManifest(manifestWithScenes(List.of(scene)));
        var si = inputs.scenes().get(0);
        assertThat(si.hostile()).isTrue();
        assertThat(si.hasLinkedEncounter()).isFalse();
        assertThat(si.anyParticipantHasStatblock()).isFalse();
        assertThat(si.encounterOperational()).isFalse();
        assertThat(si.unresolvedStatblockParticipants()).containsExactly("Goblin");
    }

    @Test
    void declaredOmissionsSurfaceAsItems() {
        var omissions = List.of(
                new ConversionOmissionDto("areas", "Not converted"),
                new ConversionOmissionDto("bestiary", "Unsupported format"));
        var manifest = manifestWithOmissions(omissions);
        var inputs = assembler.fromManifest(manifest);
        assertThat(inputs.omissions()).hasSize(2);
        assertThat(inputs.omissions().get(0).area()).isEqualTo("areas");
        assertThat(inputs.omissions().get(0).reason()).isEqualTo("Not converted");
        assertThat(inputs.omissions().get(1).area()).isEqualTo("bestiary");
        assertThat(inputs.omissions().get(1).reason()).isEqualTo("Unsupported format");
    }

    @Test
    void previewAndPersistedAssemblersAgreeOnBlockerShape() {
        var scout = new SceneParticipantDto("Goblin Scout", 2, "HOSTILE", null, null, null, null, null, 1);
        var scene = new SceneDto("s-b", "Bridge", null, "UNVISITED", 1,
                null, null, null, null, null,
                null, null, null, null, null, null, List.of(scout), null, null, null);
        var inputs = assembler.fromManifest(manifestWithScenes(List.of(scene)));
        var si = inputs.scenes().get(0);
        assertThat(si.hostile()).isTrue();
        assertThat(si.hasLinkedEncounter()).isFalse();
        assertThat(si.anyParticipantHasStatblock()).isFalse();
        assertThat(si.encounterOperational()).isFalse();
        assertThat(si.unresolvedStatblockParticipants()).containsExactly("Goblin Scout");
        assertThat(si.id()).isNull();
        assertThat(si.declaredMapRequirement()).isNull();
        assertThat(si.hasMap()).isFalse();
        assertThat(si.title()).isEqualTo("Bridge");
    }

    private static CampaignManifestV2 manifestWithScenes(List<SceneDto> scenes) {
        var chapter = new ChapterDto("c", "Ch", "intro", 0, scenes);
        var adventure = new AdventureDto("a", "Adv", null, null, 0, List.of(chapter), null);
        return baseManifest(new CampaignManifestV2.Metadata("k", null, "gen", "cat", "sha", null, List.of()),
                List.of(adventure));
    }

    private static CampaignManifestV2 manifestWithOmissions(List<ConversionOmissionDto> omissions) {
        var metadata = new CampaignManifestV2.Metadata("k", null, "gen", "cat", "sha", null, omissions);
        return baseManifest(metadata, List.of());
    }

    private static CampaignManifestV2 baseManifest(CampaignManifestV2.Metadata metadata,
                                                    List<AdventureDto> adventures) {
        return new CampaignManifestV2(
                3,
                metadata,
                null,
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(),
                List.of(), List.of(), adventures,
        null, List.of(), List.of(),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
