package dev.hendrikhoemberg.dmhelper.encounter.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EncounterRewards(
        Integer xpTotal,
        Integer xpPerPc,
        List<EncounterCurrencyGrant> currency,
        List<EncounterRewardItem> items,
        List<ContentReference> questObjectiveRefs,
        String notes
) {
    public static EncounterRewards empty() {
        return new EncounterRewards(null, null, List.of(), List.of(), List.of(), null);
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record EncounterCurrencyGrant(String currency, double amount) {}

@JsonInclude(JsonInclude.Include.NON_NULL)
record EncounterRewardItem(
        String customText,
        ContentReference magicItemRef,
        ContentReference equipmentItemRef,
        int quantity
) {}
