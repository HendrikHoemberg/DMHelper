package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignReadinessReportTest {

    private ReadinessItem item(ReadinessState state) {
        return item(ReadinessCategory.ENCOUNTER, state);
    }

    private ReadinessItem item(ReadinessCategory category, ReadinessState state) {
        return new ReadinessItem("k-" + category + "-" + state, category, state,
                "t", "d", ReadinessRepairKind.NONE, null);
    }

    @Test
    void sessionReadyWhenNoBlockers() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.RESOLVED), item(ReadinessState.ACCEPTED)));
        assertThat(report.sessionReady()).isTrue();
        assertThat(report.label()).isEqualTo("Ready to run · 1 advisories");
    }

    @Test
    void nothingOutstandingWhenItemsEmpty() {
        var report = new CampaignReadinessReport(List.of());
        assertThat(report.sessionReady()).isTrue();
        assertThat(report.label()).isEqualTo("Nothing outstanding");
    }

    @Test
    void readyToRunWithoutAdvisoriesWhenOnlyAccepted() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.ACCEPTED)));
        assertThat(report.sessionReady()).isTrue();
        assertThat(report.label()).isEqualTo("Ready to run");
    }

    @Test
    void blockedLabelReportsTheNumberOfRemainingBlockers() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.RESOLVED),
                        item(ReadinessCategory.MAP, ReadinessState.BLOCKER),
                        item(ReadinessCategory.STATBLOCK, ReadinessState.BLOCKER)));
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.blockerCount()).isEqualTo(2);
        assertThat(report.label()).isEqualTo("Not ready — 2 blockers");
    }

    @Test
    void blockedLabelUsesSingularForOneRemainingBlocker() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessCategory.MAP, ReadinessState.BLOCKER)));

        assertThat(report.label()).isEqualTo("Not ready — 1 blocker");
    }

    @Test
    void blockerGroupsUsePreparationOrderAndExcludeOtherStates() {
        var mapBlocker = item(ReadinessCategory.MAP, ReadinessState.BLOCKER);
        var encounterBlocker = item(ReadinessCategory.ENCOUNTER, ReadinessState.BLOCKER);
        var report = new CampaignReadinessReport(List.of(
                mapBlocker,
                item(ReadinessCategory.STATBLOCK, ReadinessState.ACCEPTED),
                encounterBlocker));

        assertThat(report.blockerGroups())
                .containsExactly(
                        org.assertj.core.api.Assertions.entry(
                                ReadinessCategory.ENCOUNTER, List.of(encounterBlocker)),
                        org.assertj.core.api.Assertions.entry(
                                ReadinessCategory.MAP, List.of(mapBlocker)));
    }
}
