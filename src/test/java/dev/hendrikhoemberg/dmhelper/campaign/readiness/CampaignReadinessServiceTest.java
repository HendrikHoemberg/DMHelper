package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignReadinessServiceTest {

    private final CampaignReadinessService service = new CampaignReadinessService();

    private ReadinessInputs inputs(ReadinessInputs.SceneInput... scenes) {
        return new ReadinessInputs(List.of(scenes), List.of(), List.of(), List.of());
    }

    @Test
    void hostileSceneWithoutEncounterOrStatblocksIsBlocker() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Ambush Brute", true,
                false, false, List.of("Ambush Brute"), null, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.ENCOUNTER);
    }

    @Test
    void seedableHostileSceneIsResolvedEncounterButFlagsMissingStatblocks() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Ambush Brute", true,
                false, true, List.of("Goblin 3"), null, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.STATBLOCK);
    }

    @Test
    void hostileSceneInfersRequiredMapWhenNotDeclared() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Ambush Brute", true,
                true, true, List.of(), null, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.MAP);
    }

    @Test
    void unsafePresentedAssetIsBlocker() {
        var asset = new ReadinessInputs.AssetInput(UUID.randomUUID(), "Page 12",
                Handout.AssetKind.SOURCE_PAGE, Handout.SafetyClassification.UNREVIEWED, true);
        var report = service.compute(
                new ReadinessInputs(List.of(), List.of(asset), List.of(), List.of()), Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER))
                .anyMatch(i -> i.category() == ReadinessCategory.ASSET);
    }

    @Test
    void acceptedBlockerBecomesAccepted() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Ambush Brute", true,
                false, false, List.of(), null, false);
        var first = service.compute(inputs(scene), Set.of());
        var blockerKeys = first.items().stream()
                .filter(ReadinessItem::acceptable)
                .map(ReadinessItem::key)
                .collect(Collectors.toSet());
        var second = service.compute(inputs(scene), blockerKeys);
        assertThat(second.sessionReady()).isTrue();
        assertThat(second.byState(ReadinessState.ACCEPTED))
                .extracting(ReadinessItem::key)
                .containsAll(blockerKeys);
    }

    @Test
    void optionalMapAbsenceIsAdvisoryNotBlocker() {
        var scene = new ReadinessInputs.SceneInput(UUID.randomUUID(), "Road", false,
                false, false, List.of(), SceneMapRequirement.OPTIONAL, false);
        var report = service.compute(inputs(scene), Set.of());
        assertThat(report.sessionReady()).isTrue();
    }
}
