package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import java.util.UUID;

public record ReadinessItem(
        String key,
        ReadinessCategory category,
        ReadinessState state,
        String title,
        String detail,
        ReadinessRepairKind repairKind,
        UUID targetId
) {
    public boolean acceptable() {
        return state == ReadinessState.BLOCKER;
    }
}
