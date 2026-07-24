package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import java.util.List;

public record CampaignReadinessReport(List<ReadinessItem> items) {
    public CampaignReadinessReport {
        items = List.copyOf(items);
    }

    public boolean sessionReady() {
        return blockerCount() == 0;
    }

    public long blockerCount() {
        return items.stream().filter(i -> i.state() == ReadinessState.BLOCKER).count();
    }

    public String label() {
        return sessionReady() ? "Session-ready" : "Valid but not session-ready";
    }

    public List<ReadinessItem> byState(ReadinessState state) {
        return items.stream().filter(i -> i.state() == state).toList();
    }
}
