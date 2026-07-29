package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class CampaignReadinessServiceTest {

    private static final UUID CAMPAIGN_ID = UUID.randomUUID();

    private final CampaignReadinessService service = new CampaignReadinessService();

    private ReadinessInputs inputs(ReadinessInputs.SceneInput... scenes) {
        return new ReadinessInputs(CAMPAIGN_ID, 4, List.of(scenes), List.of(), List.of());
    }

    private static ReadinessInputs.SceneInput sceneNeedingSeed(String title) {
        return new ReadinessInputs.SceneInput(UUID.randomUUID(), title, true,
                false, true, List.of(), null, false);
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

    @Test
    void anEmptyPartyBlocksReadiness() {
        var inputs = new ReadinessInputs(CAMPAIGN_ID, 0, List.of(), List.of(), List.of());
        var report = service.compute(inputs, Set.of());
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.byState(ReadinessState.BLOCKER))
                .extracting(ReadinessItem::title)
                .contains("No party members");
    }

    @Test
    void aPopulatedPartyDoesNotBlockReadiness() {
        var inputs = new ReadinessInputs(CAMPAIGN_ID, 4, List.of(), List.of(), List.of());
        var report = service.compute(inputs, Set.of());
        assertThat(report.byState(ReadinessState.BLOCKER)).isEmpty();
    }

    @Test
    void seedAdvisoriesCarryARepairTarget() {
        var inputs = new ReadinessInputs(CAMPAIGN_ID, 4, List.of(sceneNeedingSeed("Bereich 4")), List.of(), List.of());
        var report = service.compute(inputs, Set.of());
        assertThat(report.items())
                .filteredOn(i -> i.repairKind() == ReadinessRepairKind.SEED_ENCOUNTER)
                .isNotEmpty()
                .allSatisfy(i -> assertThat(i.targetId()).isNotNull());
    }
}
