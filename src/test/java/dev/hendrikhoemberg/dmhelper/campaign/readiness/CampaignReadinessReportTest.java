package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignReadinessReportTest {

    private ReadinessItem item(ReadinessState state) {
        return new ReadinessItem("k-" + state, ReadinessCategory.ENCOUNTER, state,
                "t", "d", ReadinessRepairKind.NONE, null);
    }

    @Test
    void sessionReadyWhenNoBlockers() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.RESOLVED), item(ReadinessState.ACCEPTED)));
        assertThat(report.sessionReady()).isTrue();
        assertThat(report.label()).isEqualTo("Session-ready");
    }

    @Test
    void notSessionReadyWithAnyBlocker() {
        var report = new CampaignReadinessReport(
                List.of(item(ReadinessState.RESOLVED), item(ReadinessState.BLOCKER)));
        assertThat(report.sessionReady()).isFalse();
        assertThat(report.blockerCount()).isEqualTo(1);
        assertThat(report.label()).isEqualTo("Valid but not session-ready");
    }
}
