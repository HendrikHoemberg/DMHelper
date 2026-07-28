package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
        return sessionReady() ? "Ready" : "Not ready — " + blockerCountLabel(blockerCount());
    }

    public List<ReadinessItem> byState(ReadinessState state) {
        return items.stream().filter(i -> i.state() == state).toList();
    }

    public String blockerCountLabel(long count) {
        return count + (count == 1 ? " blocker" : " blockers");
    }

    public Map<ReadinessCategory, List<ReadinessItem>> blockerGroups() {
        var groups = new EnumMap<ReadinessCategory, List<ReadinessItem>>(ReadinessCategory.class);
        items.stream()
                .filter(item -> item.state() == ReadinessState.BLOCKER)
                .forEach(item -> groups.computeIfAbsent(item.category(), ignored -> new java.util.ArrayList<>())
                        .add(item));
        groups.replaceAll((category, blockers) -> List.copyOf(blockers));
        return Collections.unmodifiableMap(groups);
    }
}
