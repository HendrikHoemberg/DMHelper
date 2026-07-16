package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignExportExclusion;

import java.util.ArrayList;
import java.util.List;

public record CampaignExportOptions(boolean includeCombatLog, boolean includeDiceHistory) {
    public static CampaignExportOptions complete() {
        return new CampaignExportOptions(true, true);
    }

    public List<CampaignExportExclusion> exclusions() {
        var values = new ArrayList<CampaignExportExclusion>();
        if (!includeCombatLog) values.add(CampaignExportExclusion.COMBAT_LOG);
        if (!includeDiceHistory) values.add(CampaignExportExclusion.DICE_HISTORY);
        return List.copyOf(values);
    }
}
